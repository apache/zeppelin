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

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.python.proto.CancelRequest;
import org.apache.zeppelin.python.proto.CompletionRequest;
import org.apache.zeppelin.python.proto.CompletionResponse;
import org.apache.zeppelin.python.proto.ExecuteRequest;
import org.apache.zeppelin.python.proto.ExecuteResponse;
import org.apache.zeppelin.python.proto.ExecuteStatus;
import org.apache.zeppelin.python.proto.IPythonStatus;
import org.apache.zeppelin.python.proto.StatusRequest;
import org.apache.zeppelin.python.proto.StatusResponse;
import org.apache.zeppelin.python.proto.StopRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * IPython Interpreter for Zeppelin
 */
public class IPythonInterpreter extends Interpreter implements ExecuteResultHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(IPythonInterpreter.class);

  private ExecuteWatchdog watchDog;
  private IPythonClient ipythonClient;
  private GatewayServer gatewayServer;

  private PythonZeppelinContext zeppelinContext;
  private String pythonExecutable;
  private long ipythonLaunchTimeout;
  private String additionalPythonPath;
  private String additionalPythonInitFile;
  private boolean useBuiltinPy4j = true;

  private InterpreterOutputStream interpreterOutput = new InterpreterOutputStream(LOGGER);

  public IPythonInterpreter(Properties properties) {
    super(properties);
  }

  /**
   * Sub class can customize the interpreter by adding more python packages under PYTHONPATH.
   * e.g. PySparkInterpreter
   *
   * @param additionalPythonPath
   */
  public void setAdditionalPythonPath(String additionalPythonPath) {
    LOGGER.info("setAdditionalPythonPath: " + additionalPythonPath);
    this.additionalPythonPath = additionalPythonPath;
  }

  /**
   * Sub class can customize the interpreter by running additional python init code.
   * e.g. PySparkInterpreter
   *
   * @param additionalPythonInitFile
   */
  public void setAdditionalPythonInitFile(String additionalPythonInitFile) {
    this.additionalPythonInitFile = additionalPythonInitFile;
  }

  public void setAddBulitinPy4j(boolean add) {
    this.useBuiltinPy4j = add;
  }

  @Override
  public void open() throws InterpreterException {
    try {
      if (ipythonClient != null) {
        // IPythonInterpreter might already been opened by PythonInterpreter
        return;
      }
      pythonExecutable = getProperty("zeppelin.python", "python");
      LOGGER.info("Python Exec: " + pythonExecutable);

      ipythonLaunchTimeout = Long.parseLong(
          getProperty("zeppelin.ipython.launch.timeout", "30000"));
      this.zeppelinContext = new PythonZeppelinContext(
          getInterpreterGroup().getInterpreterHookRegistry(),
          Integer.parseInt(getProperty("zeppelin.python.maxResult", "1000")));
      int ipythonPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      int jvmGatewayPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      LOGGER.info("Launching IPython Kernel at port: " + ipythonPort);
      LOGGER.info("Launching JVM Gateway at port: " + jvmGatewayPort);
      ipythonClient = new IPythonClient("127.0.0.1", ipythonPort);
      launchIPythonKernel(ipythonPort);
      setupJVMGateway(jvmGatewayPort);
    } catch (Exception e) {
      throw new RuntimeException("Fail to open IPythonInterpreter", e);
    }
  }

  public boolean checkIPythonPrerequisite() {
    ProcessBuilder processBuilder = new ProcessBuilder("pip", "freeze");
    try {
      File stderrFile = File.createTempFile("zeppelin", ".txt");
      processBuilder.redirectError(stderrFile);
      File stdoutFile = File.createTempFile("zeppelin", ".txt");
      processBuilder.redirectOutput(stdoutFile);

      Process proc = processBuilder.start();
      int ret = proc.waitFor();
      if (ret != 0) {
        LOGGER.warn("Fail to run pip freeze.\n" +
            IOUtils.toString(new FileInputStream(stderrFile)));
        return false;
      }
      String freezeOutput = IOUtils.toString(new FileInputStream(stdoutFile));
      if (!freezeOutput.contains("jupyter-client=")) {
        InterpreterContext.get().out.write("jupyter-client is not installed\n".getBytes());
        return false;
      }
      if (!freezeOutput.contains("ipykernel=")) {
        InterpreterContext.get().out.write("ipkernel is not installed\n".getBytes());
        return false;
      }
      if (!freezeOutput.contains("ipython=")) {
        InterpreterContext.get().out.write("ipython is not installed\n".getBytes());
        return false;
      }
      if (!freezeOutput.contains("grpcio=")) {
        InterpreterContext.get().out.write("grpcio is not installed\n".getBytes());
        return false;
      }
      LOGGER.info("IPython prerequisite is meet");
      return true;
    } catch (Exception e) {
      LOGGER.warn("Fail to checkIPythonPrerequisite", e);
      return false;
    }
  }

  private void setupJVMGateway(int jvmGatewayPort) throws IOException {
    gatewayServer = new GatewayServer(this, jvmGatewayPort);
    gatewayServer.start();

    InputStream input =
        getClass().getClassLoader().getResourceAsStream("grpc/python/zeppelin_python.py");
    List<String> lines = IOUtils.readLines(input);
    ExecuteResponse response = ipythonClient.block_execute(ExecuteRequest.newBuilder()
        .setCode(StringUtils.join(lines, System.lineSeparator())
            .replace("${JVM_GATEWAY_PORT}", jvmGatewayPort + "")).build());
    if (response.getStatus() == ExecuteStatus.ERROR) {
      throw new IOException("Fail to setup JVMGateway\n" + response.getOutput());
    }

    if (additionalPythonInitFile != null) {
      input = getClass().getClassLoader().getResourceAsStream(additionalPythonInitFile);
      lines = IOUtils.readLines(input);
      response = ipythonClient.block_execute(ExecuteRequest.newBuilder()
          .setCode(StringUtils.join(lines, System.lineSeparator())
              .replace("${JVM_GATEWAY_PORT}", jvmGatewayPort + "")).build());
      if (response.getStatus() == ExecuteStatus.ERROR) {
        throw new IOException("Fail to run additional Python init file: "
            + additionalPythonInitFile + "\n" + response.getOutput());
      }
    }
  }


  private void launchIPythonKernel(int ipythonPort)
      throws IOException, URISyntaxException {
    // copy the python scripts to a temp directory, then launch ipython kernel in that folder
    File tmpPythonScriptFolder = Files.createTempDirectory("zeppelin_ipython").toFile();
    String[] ipythonScripts = {"ipython_server.py", "ipython_pb2.py", "ipython_pb2_grpc.py"};
    for (String ipythonScript : ipythonScripts) {
      URL url = getClass().getClassLoader().getResource("grpc/python"
          + "/" + ipythonScript);
      FileUtils.copyURLToFile(url, new File(tmpPythonScriptFolder, ipythonScript));
    }

    CommandLine cmd = CommandLine.parse(pythonExecutable);
    cmd.addArgument(tmpPythonScriptFolder.getAbsolutePath() + "/ipython_server.py");
    cmd.addArgument(ipythonPort + "");
    DefaultExecutor executor = new DefaultExecutor();
    ProcessLogOutputStream processOutput = new ProcessLogOutputStream(LOGGER);
    executor.setStreamHandler(new PumpStreamHandler(processOutput));
    watchDog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
    executor.setWatchdog(watchDog);

    if (useBuiltinPy4j) {
      String py4jLibPath = null;
      if (System.getenv("ZEPPELIN_HOME") != null) {
        py4jLibPath = System.getenv("ZEPPELIN_HOME") + File.separator
            + PythonInterpreter.ZEPPELIN_PY4JPATH;
      } else {
        Path workingPath = Paths.get("..").toAbsolutePath();
        py4jLibPath = workingPath + File.separator + PythonInterpreter.ZEPPELIN_PY4JPATH;
      }
      if (additionalPythonPath != null) {
        // put the py4j at the end, because additionalPythonPath may already contain py4j.
        // e.g. PySparkInterpreter
        additionalPythonPath = additionalPythonPath + ":" + py4jLibPath;
      } else {
        additionalPythonPath = py4jLibPath;
      }
    }

    Map<String, String> envs = setupIPythonEnv();
    executor.execute(cmd, envs, this);

    // wait until IPython kernel is started or timeout
    long startTime = System.currentTimeMillis();
    while (true) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        LOGGER.error("Interrupted by something", e);
      }

      try {
        StatusResponse response = ipythonClient.status(StatusRequest.newBuilder().build());
        if (response.getStatus() == IPythonStatus.RUNNING) {
          LOGGER.info("IPython Kernel is Running");
          break;
        } else {
          LOGGER.info("Wait for IPython Kernel to be started");
        }
      } catch (Exception e) {
        // ignore the exception, because is may happen when grpc server has not started yet.
        LOGGER.info("Wait for IPython Kernel to be started");
      }

      if ((System.currentTimeMillis() - startTime) > ipythonLaunchTimeout) {
        throw new IOException("Fail to launch IPython Kernel in " + ipythonLaunchTimeout / 1000
            + " seconds");
      }
    }
  }

  protected Map<String, String> setupIPythonEnv() throws IOException {
    Map<String, String> envs = EnvironmentUtils.getProcEnvironment();
    if (envs.containsKey("PYTHONPATH")) {
      if (additionalPythonPath != null) {
        envs.put("PYTHONPATH", additionalPythonPath + ":" + envs.get("PYTHONPATH"));
      }
    } else {
      envs.put("PYTHONPATH", additionalPythonPath);
    }
    LOGGER.info("PYTHONPATH:" + envs.get("PYTHONPATH"));
    return envs;
  }

  @Override
  public void close() {
    if (watchDog != null) {
      LOGGER.debug("Kill IPython Process");
      ipythonClient.stop(StopRequest.newBuilder().build());
      watchDog.destroyProcess();
      gatewayServer.shutdown();
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    zeppelinContext.setGui(context.getGui());
    interpreterOutput.setInterpreterOutput(context.out);
    ExecuteResponse response =
        ipythonClient.stream_execute(ExecuteRequest.newBuilder().setCode(st).build(),
            interpreterOutput);
    try {
      interpreterOutput.getInterpreterOutput().flush();
    } catch (IOException e) {
      throw new RuntimeException("Fail to write output", e);
    }
    InterpreterResult result = new InterpreterResult(
        InterpreterResult.Code.valueOf(response.getStatus().name()));
    return result;
  }

  @Override
  public void cancel(InterpreterContext context) {
    ipythonClient.cancel(CancelRequest.newBuilder().build());
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
                                                InterpreterContext interpreterContext) {
    List<InterpreterCompletion> completions = new ArrayList<>();
    CompletionResponse response =
        ipythonClient.complete(
            CompletionRequest.getDefaultInstance().newBuilder().setCode(buf)
                .setCursor(cursor).build());
    for (int i = 0; i < response.getMatchesCount(); i++) {
      completions.add(new InterpreterCompletion(
          response.getMatches(i), response.getMatches(i), ""));
    }
    return completions;
  }

  public PythonZeppelinContext getZeppelinContext() {
    return zeppelinContext;
  }

  @Override
  public void onProcessComplete(int exitValue) {
    LOGGER.warn("Python Process is completed with exitValue: " + exitValue);
  }

  @Override
  public void onProcessFailed(ExecuteException e) {
    LOGGER.warn("Exception happens in Python Process", e);
  }

  private static class ProcessLogOutputStream extends LogOutputStream {

    private Logger logger;

    public ProcessLogOutputStream(Logger logger) {
      this.logger = logger;
    }

    @Override
    protected void processLine(String s, int i) {
      this.logger.debug("Process Output: " + s);
    }
  }
}
