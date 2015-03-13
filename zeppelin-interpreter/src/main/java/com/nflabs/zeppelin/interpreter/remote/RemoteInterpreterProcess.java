package com.nflabs.zeppelin.interpreter.remote;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;

/**
 *
 */
public class RemoteInterpreterProcess implements ExecuteResultHandler {
  Logger logger = LoggerFactory.getLogger(RemoteInterpreterProcess.class);
  AtomicInteger referenceCount;
  private DefaultExecutor executor;
  private ExecuteWatchdog watchdog;
  boolean running = false;
  int port = -1;
  private String interpreterRunner;
  private String interpreterDir;

  private GenericObjectPool<Client> clientPool;
  private Map<String, String> env;

  public RemoteInterpreterProcess(String intpRunner, String intpDir, Map<String, String> env) {
    this.interpreterRunner = intpRunner;
    this.interpreterDir = intpDir;
    this.env = env;
    referenceCount = new AtomicInteger(0);
  }

  public int getPort() {
    return port;
  }

  public int reference() {
    synchronized (referenceCount) {
      if (executor == null) {
        // start server process
        try {
          port = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
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
          Map procEnv = EnvironmentUtils.getProcEnvironment();
          procEnv.putAll(env);

          logger.info("Run interpreter process {}", cmdLine);
          executor.execute(cmdLine, procEnv, this);
        } catch (IOException e) {
          running = false;
          throw new InterpreterException(e);
        }


        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 5 * 1000) {
          if (RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost", port)) {
            break;
          } else {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
            }
          }
        }

        clientPool = new GenericObjectPool<Client>(new ClientFactory("localhost", port));
      }
      return referenceCount.incrementAndGet();
    }
  }

  public Client getClient() throws Exception {
    return clientPool.borrowObject();
  }

  public void releaseClient(Client client) {
    clientPool.returnObject(client);
  }

  public int dereference() {
    synchronized (referenceCount) {
      int r = referenceCount.decrementAndGet();
      if (r == 0) {
        logger.info("shutdown interpreter process");
        // first try shutdown
        try {
          Client client = getClient();
          client.shutdown();
          releaseClient(client);
        } catch (Exception e) {
          logger.error("Error", e);
          watchdog.destroyProcess();
        }

        clientPool.clear();
        clientPool.close();

        // wait for 3 sec and force kill
        // remote process server.serve() loop is not always finishing gracefully
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 3 * 1000) {
          if (this.isRunning()) {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
            }
          } else {
            break;
          }
        }

        if (isRunning()) {
          logger.info("kill interpreter process");
          watchdog.destroyProcess();
        }

        executor = null;
        watchdog = null;
        running = false;
        logger.info("Remote process terminated");
      }
      return r;
    }
  }

  public int referenceCount() {
    synchronized (referenceCount) {
      return referenceCount.get();
    }
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

  public int getNumActiveClient() {
    if (clientPool == null) {
      return 0;
    } else {
      return clientPool.getNumActive();
    }
  }

  public int getNumIdleClient() {
    if (clientPool == null) {
      return 0;
    } else {
      return clientPool.getNumIdle();
    }
  }
}
