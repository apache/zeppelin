package com.nflabs.zeppelin.interpreter.remote;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;

import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterService;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;

/**
 *
 */
public class RemoteInterpreterProcess implements ExecuteResultHandler {
  AtomicInteger referenceCount;
  private DefaultExecutor executor;
  private ExecuteWatchdog watchdog;
  boolean running = false;
  int port = -1;
  private String interpreterRunner;
  private String interpreterDir;
  private Client client;
  private TSocket transport;

  public RemoteInterpreterProcess(String intpRunner, String intpDir) {
    this.interpreterRunner = intpRunner;
    this.interpreterDir = intpDir;
    referenceCount = new AtomicInteger(0);

  }

  public int getPort() {
    return port;
  }

  private int findRandomOpenPortOnAllLocalInterfaces() throws IOException {
    try (ServerSocket socket = new ServerSocket(0);) {
      port = socket.getLocalPort();
      socket.close();
    }
    return port;
  }

  public int reference() {
    synchronized (referenceCount) {
      if (executor == null) {
        // start server process
        try {
          findRandomOpenPortOnAllLocalInterfaces();
        } catch (IOException e1) {
          throw new InterpreterException(e1);
        }


        CommandLine cmdLine = CommandLine.parse(interpreterRunner);
        cmdLine.addArgument("-d", false);
        cmdLine.addArgument(interpreterDir, false);
        cmdLine.addArgument("-p", false);
        cmdLine.addArgument(Integer.toString(port), false);

        executor = new DefaultExecutor();

        watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
        executor.setWatchdog(watchdog);

        running = true;
        try {
          executor.execute(cmdLine, this);
        } catch (IOException e) {
          running = false;
          throw new InterpreterException(e);
        }
      }

      try {
        Thread.sleep(5000);
      } catch (InterruptedException e1) {
      }
      transport = new TSocket("localhost", port);
      try {
        transport.open();
      } catch (TTransportException e) {
        throw new InterpreterException(e);
      }

      TProtocol protocol = new  TBinaryProtocol(transport);
      client = new RemoteInterpreterService.Client(protocol);

      return referenceCount.incrementAndGet();
    }
  }

  public Client getClient() {
    return client;
  }

  public int dereference() {
    synchronized (referenceCount) {
      int r = referenceCount.decrementAndGet();
      if (r == 0) {
        transport.close();

        // terminate server process
        watchdog.destroyProcess();
        executor = null;
        watchdog = null;
      }
      return r;
    }
  }

  @Override
  public void onProcessComplete(int exitValue) {
    running = false;

  }

  @Override
  public void onProcessFailed(ExecuteException e) {
    running = false;
  }
}
