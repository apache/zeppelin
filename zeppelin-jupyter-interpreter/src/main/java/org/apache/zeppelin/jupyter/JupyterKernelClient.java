/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.zeppelin.jupyter;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.jupyter.proto.JupyterKernelGrpc;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.interpreter.jupyter.proto.CancelRequest;
import org.apache.zeppelin.interpreter.jupyter.proto.CancelResponse;
import org.apache.zeppelin.interpreter.jupyter.proto.CompletionRequest;
import org.apache.zeppelin.interpreter.jupyter.proto.CompletionResponse;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteRequest;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteResponse;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteStatus;
import org.apache.zeppelin.interpreter.jupyter.proto.OutputType;
import org.apache.zeppelin.interpreter.jupyter.proto.StatusRequest;
import org.apache.zeppelin.interpreter.jupyter.proto.StatusResponse;
import org.apache.zeppelin.interpreter.jupyter.proto.StopRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grpc client for Jupyter kernel
 */
public class JupyterKernelClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(JupyterKernelClient.class.getName());
  // used for matching shiny url
  private static Pattern ShinyListeningPattern =
          Pattern.compile(".*Listening on (http:\\S*).*", Pattern.DOTALL);

  private final ManagedChannel channel;
  private final JupyterKernelGrpc.JupyterKernelBlockingStub blockingStub;
  private final JupyterKernelGrpc.JupyterKernelStub asyncStub;
  private volatile boolean maybeKernelFailed = false;

  private Properties properties;
  private InterpreterContext context;
  private SecureRandom random = new SecureRandom();

  /**
   * Construct client for accessing RouteGuide server at {@code host:port}.
   */
  public JupyterKernelClient(String host,
                             int port) {
    this(ManagedChannelBuilder.forAddress(host, port).usePlaintext(true), new Properties());
  }

  /**
   * Construct client for accessing RouteGuide server using the existing channel.
   */
  public JupyterKernelClient(ManagedChannelBuilder<?> channelBuilder, Properties properties) {
    channel = channelBuilder.build();
    blockingStub = JupyterKernelGrpc.newBlockingStub(channel);
    asyncStub = JupyterKernelGrpc.newStub(channel);
    this.properties = properties;
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /**
   * set current InterpreterContext.
   * @param context
   */
  public void setInterpreterContext(InterpreterContext context) {
    this.context = context;
  }

  /**
   * This is for shiny interpreter. It's better not to put this in the general
   * JupyterKernelClient, we may need to create a specififc JupyterKernelClient for R Kernel.
   * @param response
   * @return true if shiny url is matched
   * @throws IOException
   */
  private boolean checkForShinyApp(String response) throws IOException {
    if (context.getInterpreterClassName() != null &&
            context.getInterpreterClassName().equals("org.apache.zeppelin.r.ShinyInterpreter")) {
      Matcher matcher = ShinyListeningPattern.matcher(response);
      if (matcher.matches()) {
        String url = matcher.group(1);
        LOGGER.info("Matching shiny app url: " + url);
        context.out.clear();
        String defaultHeight = properties.getProperty("zeppelin.R.shiny.iframe_height", "500px");
        String height = context.getLocalProperties().getOrDefault("height", defaultHeight);
        String defaultWidth = properties.getProperty("zeppelin.R.shiny.iframe_width", "100%");
        String width = context.getLocalProperties().getOrDefault("width", defaultWidth);
        context.out.write("\n%html " + "<iframe src=\"" + url + "\" height =\"" +
                height + "\" width=\"" + width + "\" frameBorder=\"0\"></iframe>");
        context.out.flush();
        context.out.write("\n%text ");
        context.getIntpEventClient().checkpointOutput(context.getNoteId(),
                context.getParagraphId());
        return true;
      }
    }
    return false;
  }

  // execute the code and make the output as streaming by writing it to InterpreterOutputStream
  // one by one.
  public ExecuteResponse stream_execute(ExecuteRequest request,
                                        final InterpreterOutputStream interpreterOutput) {
    final ExecuteResponse.Builder finalResponseBuilder = ExecuteResponse.newBuilder()
        .setStatus(ExecuteStatus.SUCCESS);
    final AtomicBoolean completedFlag = new AtomicBoolean(false);
    maybeKernelFailed = false;
    LOGGER.debug("stream_execute code:\n" + request.getCode());
    asyncStub.execute(request, new StreamObserver<ExecuteResponse>() {
      OutputType lastOutputType = null;

      @Override
      public void onNext(ExecuteResponse executeResponse) {
        LOGGER.debug("Interpreter Streaming Output: " + executeResponse.getType() +
                "\t" + executeResponse.getOutput());
        switch (executeResponse.getType()) {
          case TEXT:
            try {
              if (checkForShinyApp(executeResponse.getOutput())) {
                break;
              }
              if (executeResponse.getOutput().startsWith("%")) {
                // the output from jupyter kernel maybe specify format already.
                interpreterOutput.write((executeResponse.getOutput()).getBytes());
              } else {
                // only add %text when the previous output type is not TEXT.
                // Reason :
                // 1. if no `%text`, it will be treated as previous output type.
                // 2. Always prepend `%text `, there will be an extra line separator,
                // because `%text ` appends line separator first.
                if (lastOutputType != OutputType.TEXT) {
                  interpreterOutput.write("%text ".getBytes());
                }
                interpreterOutput.write(executeResponse.getOutput().getBytes());
              }
              interpreterOutput.getInterpreterOutput().flush();
            } catch (IOException e) {
              LOGGER.error("Unexpected IOException", e);
            }
            break;
          case PNG:
          case JPEG:
            try {
              interpreterOutput.write(("\n%img " + executeResponse.getOutput()).getBytes());
              interpreterOutput.getInterpreterOutput().flush();
            } catch (IOException e) {
              LOGGER.error("Unexpected IOException", e);
            }
            break;
          case HTML:
            try {
              interpreterOutput.write(("\n%html " + executeResponse.getOutput()).getBytes());
              interpreterOutput.getInterpreterOutput().flush();
            } catch (IOException e) {
              LOGGER.error("Unexpected IOException", e);
            }
            break;
          default:
            LOGGER.error("Unrecognized type:" + executeResponse.getType());
        }

        lastOutputType = executeResponse.getType();
        if (executeResponse.getStatus() == ExecuteStatus.ERROR) {
          // set the finalResponse to ERROR if any ERROR happens, otherwise the finalResponse would
          // be SUCCESS.
          finalResponseBuilder.setStatus(ExecuteStatus.ERROR);
        }
      }

      @Override
      public void onError(Throwable throwable) {
        try {
          interpreterOutput.getInterpreterOutput().write(ExceptionUtils.getStackTrace(throwable));
          interpreterOutput.getInterpreterOutput().flush();
        } catch (IOException e) {
          LOGGER.error("Unexpected IOException", e);
        }
        LOGGER.error("Fail to call IPython grpc", throwable);
        finalResponseBuilder.setStatus(ExecuteStatus.ERROR);
        maybeKernelFailed = true;
        completedFlag.set(true);
        synchronized (completedFlag) {
          completedFlag.notify();
        }
      }

      @Override
      public void onCompleted() {
        synchronized (completedFlag) {
          try {
            LOGGER.debug("stream_execute is completed");
            interpreterOutput.getInterpreterOutput().flush();
          } catch (IOException e) {
            LOGGER.error("Unexpected IOException", e);
          }
          completedFlag.set(true);
          completedFlag.notify();
        }
      }
    });

    synchronized (completedFlag) {
      if (!completedFlag.get()) {
        try {
          completedFlag.wait();
        } catch (InterruptedException e) {
          LOGGER.error("Unexpected Interruption", e);
        }
      }
    }
    return finalResponseBuilder.build();
  }

  // blocking execute the code
  public ExecuteResponse block_execute(ExecuteRequest request) {
    ExecuteResponse.Builder responseBuilder = ExecuteResponse.newBuilder();
    responseBuilder.setStatus(ExecuteStatus.SUCCESS);
    Iterator<ExecuteResponse> iter = blockingStub.execute(request);
    StringBuilder outputBuilder = new StringBuilder();
    try {
      // iter.hasNext may throw exception, so use try catch outside.
      while (iter.hasNext()) {
        ExecuteResponse nextResponse = iter.next();
        if (nextResponse.getStatus() == ExecuteStatus.ERROR) {
          responseBuilder.setStatus(ExecuteStatus.ERROR);
        }
        outputBuilder.append(nextResponse.getOutput());
      }
      responseBuilder.setOutput(outputBuilder.toString());
    } catch (Exception e) {
      responseBuilder.setStatus(ExecuteStatus.ERROR);
      responseBuilder.setOutput(outputBuilder.toString());
    }

    return responseBuilder.build();
  }

  public CancelResponse cancel(CancelRequest request) {
    return blockingStub.cancel(request);
  }

  public CompletionResponse complete(CompletionRequest request) {
    return blockingStub.complete(request);
  }

  public StatusResponse status(StatusRequest request) {
    return blockingStub.status(request);
  }

  public void stop(StopRequest request) {
    asyncStub.stop(request, null);
  }

  public boolean isMaybeKernelFailed() {
    return maybeKernelFailed;
  }

  public static void main(String[] args) {
    JupyterKernelClient client = new JupyterKernelClient("localhost", 50053);
    client.status(StatusRequest.newBuilder().build());

    ExecuteResponse response = client.block_execute(ExecuteRequest.newBuilder().
        setCode("abcd=2").build());
    System.out.println(response.getOutput());
  }
}
