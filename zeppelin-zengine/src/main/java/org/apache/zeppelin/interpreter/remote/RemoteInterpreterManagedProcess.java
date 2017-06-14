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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterException;

/**
 * This class manages start / stop of remote interpreter process
 */
public class RemoteInterpreterManagedProcess extends RemoteInterpreterProcess
    implements ExecuteResultHandler {
  private static final Logger logger = LoggerFactory.getLogger(
      RemoteInterpreterManagedProcess.class);
  private final String interpreterRunner;
  private final CountDownLatch hostPortLatch;

  private DefaultExecutor executor;
  private ExecuteWatchdog watchdog;
  boolean running = false;
  private String host = null;
  private int port = -1;
  private final String interpreterDir;
  private final String localRepoDir;
  private final String interpreterGroupName;

  private Map<String, String> env;


  public RemoteInterpreterManagedProcess(
      String intpRunner,
      String intpDir,
      String localRepoDir,
      Map<String, String> env,
      int connectTimeout,
      RemoteInterpreterProcessListener listener,
      ApplicationEventListener appListener,
      String interpreterGroupName) {
    super(new RemoteInterpreterEventPoller(listener, appListener),
        connectTimeout);
    this.interpreterRunner = intpRunner;
    this.env = env;
    this.interpreterDir = intpDir;
    this.localRepoDir = localRepoDir;
    this.interpreterGroupName = interpreterGroupName;
    this.hostPortLatch = new CountDownLatch(1);
  }

  RemoteInterpreterManagedProcess(String intpRunner,
                                  String intpDir,
                                  String localRepoDir,
                                  Map<String, String> env,
                                  RemoteInterpreterEventPoller remoteInterpreterEventPoller,
                                  int connectTimeout,
                                  String interpreterGroupName) {
    super(remoteInterpreterEventPoller,
        connectTimeout);
    this.interpreterRunner = intpRunner;
    this.env = env;
    this.interpreterDir = intpDir;
    this.localRepoDir = localRepoDir;
    this.interpreterGroupName = interpreterGroupName;
    this.hostPortLatch = new CountDownLatch(1);
  }

  @Override
  public String getHost() {
    return "localhost";
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void start(String userName, Boolean isUserImpersonate) {
    // start server process
    final String callbackHost;
    final int callbackPort;
    try {
      callbackHost = RemoteInterpreterUtils.findAvailableHostname();
      callbackPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
    } catch (IOException e1) {
      throw new InterpreterException(e1);
    }

    CommandLine cmdLine = CommandLine.parse(interpreterRunner);
    cmdLine.addArgument("-d", false);
    cmdLine.addArgument(interpreterDir, false);
    cmdLine.addArgument("-c", false);
    cmdLine.addArgument(callbackHost, false);
    cmdLine.addArgument("-p", false);
    cmdLine.addArgument(Integer.toString(callbackPort), false);
    if (isUserImpersonate && !userName.equals("anonymous")) {
      cmdLine.addArgument("-u", false);
      cmdLine.addArgument(userName, false);
    }
    cmdLine.addArgument("-l", false);
    cmdLine.addArgument(localRepoDir, false);
    cmdLine.addArgument("-g", false);
    cmdLine.addArgument(interpreterGroupName, false);

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
      running = true;
    } catch (IOException e) {
      running = false;
      throw new InterpreterException(e);
    }

    // Start netty server to receive hostPort information from RemoteInterpreterServer;
    new Thread(new Runnable() {
      @Override
      public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
          ServerBootstrap b = new ServerBootstrap();
          b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
              .handler(new LoggingHandler(LogLevel.DEBUG))
              .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                  ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                    @Override
                    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                      logger.info("{}", ctx);
                    }

                    @Override
                    public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                      logger.info("msg: {}", msg.toString(CharsetUtil.UTF_8));
                      String[] hostPort = msg.toString(CharsetUtil.UTF_8).split(":");
                      host = hostPort[0];
                      port = Integer.parseInt(hostPort[1]);
                      hostPortLatch.countDown();
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                      logger.error("Netty error", cause);
                      ctx.close();
                    }
                  });
                }
              });

          logger.info("Netty server starts");
          // Bind and start to accept incoming connections.
          ChannelFuture f = b.bind(callbackPort).sync();

          // Wait until the server socket is closed.
          // In this example, this does not happen, but you can do that to gracefully
          // shut down your server.
          f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
          logger.error("Netty server error while binding", e);
        } finally {
          workerGroup.shutdownGracefully();
          bossGroup.shutdownGracefully();
        }
      }
    }).start();

    try {
      hostPortLatch.await(getConnectTimeout(), TimeUnit.MILLISECONDS);
      // Check if not running
      if (null == host || -1 == port) {
        throw new InterpreterException("CAnnot run interpreter");
      }
    } catch (InterruptedException e) {
      logger.error("Remote interpreter is not accessible");
    }
    processOutput.setOutputStream(null);
  }

  public void stop() {
    if (isRunning()) {
      logger.info("kill interpreter process");
      watchdog.destroyProcess();
    }

    executor = null;
    watchdog = null;
    running = false;
    logger.info("Remote process terminated");
  }

  @Override
  public void onProcessComplete(int exitValue) {
    logger.info("Interpreter process exited {}", exitValue);
    running = false;

  }

  @Override
  public void onProcessFailed(ExecuteException e) {
    logger.info("Interpreter process failed {}", e);
    running = false;
  }

  public boolean isRunning() {
    return running;
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
