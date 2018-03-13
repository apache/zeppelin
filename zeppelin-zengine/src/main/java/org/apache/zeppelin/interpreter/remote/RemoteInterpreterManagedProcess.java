/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.remote;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.thrift.TException;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.interpreter.thrift.CallbackInfo;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterCallbackService;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class manages start / stop of remote interpreter process
 */
public class RemoteInterpreterManagedProcess extends RemoteInterpreterProcess
    implements ExecuteResultHandler {
  private static final Logger logger = LoggerFactory.getLogger(
      RemoteInterpreterManagedProcess.class);

  private final String interpreterRunner;
  private final String callbackPortRange;
  private final String interpreterPortRange;
  private DefaultExecutor executor;
  private ExecuteWatchdog watchdog;
  private AtomicBoolean running = new AtomicBoolean(false);
  private TServer callbackServer;
  private String host = null;
  private int port = -1;
  private final String interpreterDir;
  private final String localRepoDir;
  private final String interpreterSettingName;
  private final boolean isUserImpersonated;

  private Map<String, String> env;

  public RemoteInterpreterManagedProcess(
      String intpRunner,
      String callbackPortRange,
      String interpreterPortRange,
      String intpDir,
      String localRepoDir,
      Map<String, String> env,
      int connectTimeout,
      String interpreterSettingName,
      boolean isUserImpersonated) {
    super(connectTimeout);
    this.interpreterRunner = intpRunner;
    this.callbackPortRange = callbackPortRange;
    this.interpreterPortRange = interpreterPortRange;
    this.env = env;
    this.interpreterDir = intpDir;
    this.localRepoDir = localRepoDir;
    this.interpreterSettingName = interpreterSettingName;
    this.isUserImpersonated = isUserImpersonated;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void start(String userName) {
    // start server process
    final String callbackHost;
    final int callbackPort;
    TServerSocket tSocket = null;
    try {
      tSocket = RemoteInterpreterUtils.createTServerSocket(callbackPortRange);
      callbackPort = tSocket.getServerSocket().getLocalPort();
      callbackHost = RemoteInterpreterUtils.findAvailableHostAddress();
    } catch (IOException e1) {
      throw new RuntimeException(e1);
    }

    logger.info("Thrift server for callback will start. Port: {}", callbackPort);
    try {
      callbackServer = new TThreadPoolServer(
        new TThreadPoolServer.Args(tSocket).processor(
          new RemoteInterpreterCallbackService.Processor<>(
            new RemoteInterpreterCallbackService.Iface() {
              @Override
              public void callback(CallbackInfo callbackInfo) throws TException {
                logger.info("RemoteInterpreterServer Registered: {}", callbackInfo);
                host = callbackInfo.getHost();
                port = callbackInfo.getPort();
                running.set(true);
                synchronized (running) {
                  running.notify();
                }
              }
            })));
      // Start thrift server to receive callbackInfo from RemoteInterpreterServer;
      new Thread(new Runnable() {
        @Override
        public void run() {
          callbackServer.serve();
        }
      }).start();

      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override
        public void run() {
          if (callbackServer.isServing()) {
            callbackServer.stop();
          }
        }
      }));

      while (!callbackServer.isServing()) {
        logger.debug("callbackServer is not serving");
        Thread.sleep(500);
      }
      logger.debug("callbackServer is serving now");
    } catch (InterruptedException e) {
      logger.warn("", e);
    }

    CommandLine cmdLine = CommandLine.parse(interpreterRunner);
    cmdLine.addArgument("-d", false);
    cmdLine.addArgument(interpreterDir, false);
    cmdLine.addArgument("-c", false);
    cmdLine.addArgument(callbackHost, false);
    cmdLine.addArgument("-p", false);
    cmdLine.addArgument(Integer.toString(callbackPort), false);
    cmdLine.addArgument("-r", false);
    cmdLine.addArgument(interpreterPortRange, false);
    if (isUserImpersonated && !userName.equals("anonymous")) {
      cmdLine.addArgument("-u", false);
      cmdLine.addArgument(userName, false);
    }
    cmdLine.addArgument("-l", false);
    cmdLine.addArgument(localRepoDir, false);
    cmdLine.addArgument("-g", false);
    cmdLine.addArgument(interpreterSettingName, false);

    executor = new DefaultExecutor();

    ByteArrayOutputStream cmdOut = new ByteArrayOutputStream();
    ProcessLogOutputStream processOutput = new ProcessLogOutputStream(logger);
    processOutput.setOutputStream(cmdOut);

    executor.setStreamHandler(new PumpStreamHandler(processOutput));
    watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
    executor.setWatchdog(watchdog);

    try {
      Map procEnv = EnvironmentUtils.getProcEnvironment();
      procEnv.putAll(env);

      logger.info("Run interpreter process {}", cmdLine);
      executor.execute(cmdLine, procEnv, this);
    } catch (IOException e) {
      running.set(false);
      throw new RuntimeException(e);
    }

    try {
      synchronized (running) {
        if (!running.get()) {
          running.wait(getConnectTimeout() * 2);
        }
      }
      if (!running.get()) {
        callbackServer.stop();
        throw new RuntimeException(new String(cmdOut.toByteArray()));
      }
    } catch (InterruptedException e) {
      logger.error("Remote interpreter is not accessible");
    }
    processOutput.setOutputStream(null);
  }

  public void stop() {
    // shutdown EventPoller first.
    this.getRemoteInterpreterEventPoller().shutdown();
    if (callbackServer.isServing()) {
      callbackServer.stop();
    }
    if (isRunning()) {
      logger.info("Kill interpreter process");
      try {
        callRemoteFunction(new RemoteFunction<Void>() {
          @Override
          public Void call(RemoteInterpreterService.Client client) throws Exception {
            client.shutdown();
            return null;
          }
        });
      } catch (Exception e) {
        logger.warn("ignore the exception when shutting down");
      }
      watchdog.destroyProcess();
    }

    executor = null;
    watchdog = null;
    running.set(false);
    logger.info("Remote process terminated");
  }

  @Override
  public void onProcessComplete(int exitValue) {
    logger.info("Interpreter process exited {}", exitValue);
    running.set(false);

  }

  @Override
  public void onProcessFailed(ExecuteException e) {
    logger.info("Interpreter process failed {}", e);
    running.set(false);
  }

  @VisibleForTesting
  public Map<String, String> getEnv() {
    return env;
  }

  @VisibleForTesting
  public String getLocalRepoDir() {
    return localRepoDir;
  }

  @VisibleForTesting
  public String getInterpreterDir() {
    return interpreterDir;
  }

  public String getInterpreterSettingName() {
    return interpreterSettingName;
  }

  @VisibleForTesting
  public String getInterpreterRunner() {
    return interpreterRunner;
  }

  @VisibleForTesting
  public boolean isUserImpersonated() {
    return isUserImpersonated;
  }

  public boolean isRunning() {
    return running.get();
  }

  private static class ProcessLogOutputStream extends LogOutputStream {

    private Logger logger;
    OutputStream out;

    public ProcessLogOutputStream(Logger logger) {
      this.logger = logger;
    }

    @Override
    protected void processLine(String s, int i) {
      this.logger.debug(s);
    }

    @Override
    public void write(byte [] b) throws IOException {
      super.write(b);

      if (out != null) {
        synchronized (this) {
          if (out != null) {
            out.write(b);
          }
        }
      }
    }

    @Override
    public void write(byte [] b, int offset, int len) throws IOException {
      super.write(b, offset, len);

      if (out != null) {
        synchronized (this) {
          if (out != null) {
            out.write(b, offset, len);
          }
        }
      }
    }

    public void setOutputStream(OutputStream out) {
      synchronized (this) {
        this.out = out;
      }
    }
  }
}
