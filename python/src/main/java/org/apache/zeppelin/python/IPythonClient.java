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

package org.apache.zeppelin.python;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.python.proto.CancelRequest;
import org.apache.zeppelin.python.proto.CancelResponse;
import org.apache.zeppelin.python.proto.CompletionRequest;
import org.apache.zeppelin.python.proto.CompletionResponse;
import org.apache.zeppelin.python.proto.ExecuteRequest;
import org.apache.zeppelin.python.proto.ExecuteResponse;
import org.apache.zeppelin.python.proto.ExecuteStatus;
import org.apache.zeppelin.python.proto.IPythonGrpc;
import org.apache.zeppelin.python.proto.OutputType;
import org.apache.zeppelin.python.proto.StatusRequest;
import org.apache.zeppelin.python.proto.StatusResponse;
import org.apache.zeppelin.python.proto.StopRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Grpc client for IPython kernel
 */
public class IPythonClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(IPythonClient.class.getName());

  private final ManagedChannel channel;
  private final IPythonGrpc.IPythonBlockingStub blockingStub;
  private final IPythonGrpc.IPythonStub asyncStub;

  private SecureRandom random = new SecureRandom();

  /**
   * Construct client for accessing RouteGuide server at {@code host:port}.
   */
  public IPythonClient(String host, int port) {
    this(ManagedChannelBuilder.forAddress(host, port).usePlaintext(true));
  }

  /**
   * Construct client for accessing RouteGuide server using the existing channel.
   */
  public IPythonClient(ManagedChannelBuilder<?> channelBuilder) {
    channel = channelBuilder.build();
    blockingStub = IPythonGrpc.newBlockingStub(channel);
    asyncStub = IPythonGrpc.newStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  // execute the code and make the output as streaming by writing it to InterpreterOutputStream
  // one by one.
  public ExecuteResponse stream_execute(ExecuteRequest request,
                                        final InterpreterOutputStream interpreterOutput) {
    final ExecuteResponse.Builder finalResponseBuilder = ExecuteResponse.newBuilder()
        .setStatus(ExecuteStatus.SUCCESS);
    final AtomicBoolean completedFlag = new AtomicBoolean(false);
    LOGGER.debug("stream_execute code:\n" + request.getCode());
    asyncStub.execute(request, new StreamObserver<ExecuteResponse>() {
      int index = 0;
      boolean isPreviousOutputImage = false;

      @Override
      public void onNext(ExecuteResponse executeResponse) {
        if (executeResponse.getType() == OutputType.TEXT) {
          try {
            LOGGER.debug("Interpreter Streaming Output: " + executeResponse.getOutput());
            if (isPreviousOutputImage) {
              // add '\n' when switch from image to text
              interpreterOutput.write("\n%text ".getBytes());
            }
            isPreviousOutputImage = false;
            interpreterOutput.write(executeResponse.getOutput().getBytes());
            interpreterOutput.getInterpreterOutput().flush();
          } catch (IOException e) {
            LOGGER.error("Unexpected IOException", e);
          }
        }
        if (executeResponse.getType() == OutputType.IMAGE) {
          try {
            LOGGER.debug("Interpreter Streaming Output: IMAGE_DATA");
            if (index != 0) {
              // add '\n' if this is the not the first element. otherwise it would mix the image
              // with the text
              interpreterOutput.write("\n".getBytes());
            }
            interpreterOutput.write(("%img " + executeResponse.getOutput()).getBytes());
            interpreterOutput.getInterpreterOutput().flush();
            isPreviousOutputImage = true;
          } catch (IOException e) {
            LOGGER.error("Unexpected IOException", e);
          }
        }
        if (executeResponse.getStatus() == ExecuteStatus.ERROR) {
          // set the finalResponse to ERROR if any ERROR happens, otherwise the finalResponse would
          // be SUCCESS.
          finalResponseBuilder.setStatus(ExecuteStatus.ERROR);
        }
        index++;
      }

      @Override
      public void onError(Throwable throwable) {
        try {
          interpreterOutput.getInterpreterOutput().flush();
        } catch (IOException e) {
          LOGGER.error("Unexpected IOException", e);
        }
        LOGGER.error("Fail to call IPython grpc", throwable);
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
    while (iter.hasNext()) {
      ExecuteResponse nextResponse = iter.next();
      if (nextResponse.getStatus() == ExecuteStatus.ERROR) {
        responseBuilder.setStatus(ExecuteStatus.ERROR);
      }
      outputBuilder.append(nextResponse.getOutput());
    }
    responseBuilder.setOutput(outputBuilder.toString());
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


  public static void main(String[] args) {
    IPythonClient client = new IPythonClient("localhost", 50053);
    client.status(StatusRequest.newBuilder().build());

    ExecuteResponse response = client.block_execute(ExecuteRequest.newBuilder().
        setCode("abcd=2").build());
    System.out.println(response.getOutput());

  }
}
