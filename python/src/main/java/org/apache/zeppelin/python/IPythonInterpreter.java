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

import com.google.common.annotations.VisibleForTesting;
import io.grpc.ManagedChannelBuilder;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
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
import org.apache.zeppelin.util.ProcessLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * IPython Interpreter for Zeppelin
 */
public class IPythonInterpreter extends Interpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IPythonInterpreter.class);

  private IPythonProcessLauncher iPythonProcessLauncher;
  private IPythonClient ipythonClient;
  private GatewayServer gatewayServer;

  protected BaseZeppelinContext zeppelinContext;
  private String pythonExecutable;
  private int ipythonLaunchTimeout;
  private String additionalPythonPath;
  private String additionalPythonInitFile;
  private boolean useBuiltinPy4j = true;
  private boolean usePy4JAuth = true;
  private String secret;

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

  public BaseZeppelinContext buildZeppelinContext() {
    return new PythonZeppelinContext(
        getInterpreterGroup().getInterpreterHookRegistry(),
        Integer.parseInt(getProperty("zeppelin.python.maxResult", "1000")));
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
      String checkPrerequisiteResult = checkIPythonPrerequisite(pythonExecutable);
      if (!StringUtils.isEmpty(checkPrerequisiteResult)) {
        throw new InterpreterException("IPython prerequisite is not meet: " +
            checkPrerequisiteResult);
      }
      ipythonLaunchTimeout = Integer.parseInt(
          getProperty("zeppelin.ipython.launch.timeout", "30000"));
      this.zeppelinContext = buildZeppelinContext();
      int ipythonPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      int jvmGatewayPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      int message_size = Integer.parseInt(getProperty("zeppelin.ipython.grpc.message_size",
          32 * 1024 * 1024 + ""));
      ipythonClient = new IPythonClient(ManagedChannelBuilder.forAddress("127.0.0.1", ipythonPort)
          .usePlaintext(true).maxInboundMessageSize(message_size));
      this.usePy4JAuth = Boolean.parseBoolean(getProperty("zeppelin.py4j.useAuth", "true"));
      this.secret = PythonUtils.createSecret(256);
      launchIPythonKernel(ipythonPort);
      setupJVMGateway(jvmGatewayPort);
    } catch (Exception e) {
      throw new InterpreterException("Fail to open IPythonInterpreter", e);
    }
  }

  /**
   * non-empty return value mean the errors when checking ipython prerequisite.
   * empty value mean IPython prerequisite is meet.
   *
   * @param pythonExec
   * @return
   */
  public String checkIPythonPrerequisite(String pythonExec) {
    ProcessBuilder processBuilder = new ProcessBuilder(pythonExec, "-m", "pip", "freeze");
    File stderrFile = null;
    File stdoutFile = null;
    try {
      stderrFile = File.createTempFile("zeppelin", ".txt");
      processBuilder.redirectError(stderrFile);
      stdoutFile = File.createTempFile("zeppelin", ".txt");
      processBuilder.redirectOutput(stdoutFile);

      Process proc = processBuilder.start();
      int ret = proc.waitFor();
      if (ret != 0) {
        try (FileInputStream in = new FileInputStream(stderrFile)) {
          return "Fail to run pip freeze.\n" + IOUtils.toString(in);
        }
      }
      try (FileInputStream in = new FileInputStream(stdoutFile)) {
        String freezeOutput = IOUtils.toString(in);
        if (!freezeOutput.contains("jupyter-client=")) {
          return "jupyter-client is not installed.";
        }
        if (!freezeOutput.contains("ipykernel=")) {
          return "ipykernel is not installed";
        }
        if (!freezeOutput.contains("ipython=")) {
          return "ipython is not installed";
        }
        if (!freezeOutput.contains("grpcio=")) {
          return "grpcio is not installed";
        }
        if (!freezeOutput.contains("protobuf=")) {
          return "protobuf is not installed";
        }
        LOGGER.info("IPython prerequisite is met");
      }
    } catch (Exception e) {
      LOGGER.warn("Fail to checkIPythonPrerequisite", e);
      return "Fail to checkIPythonPrerequisite: " + ExceptionUtils.getStackTrace(e);
    } finally {
      FileUtils.deleteQuietly(stderrFile);
      FileUtils.deleteQuietly(stdoutFile);
    }
    return "";
  }

  private void setupJVMGateway(int jvmGatewayPort) throws IOException {
    String serverAddress = PythonUtils.getLocalIP(properties);
    this.gatewayServer =
        PythonUtils.createGatewayServer(this, serverAddress, jvmGatewayPort, secret, usePy4JAuth);
    gatewayServer.start();

    InputStream input =
        getClass().getClassLoader().getResourceAsStream("grpc/python/zeppelin_python.py");
    List<String> lines = IOUtils.readLines(input);
    ExecuteResponse response = ipythonClient.block_execute(ExecuteRequest.newBuilder()
        .setCode(StringUtils.join(lines, System.lineSeparator())
            .replace("${JVM_GATEWAY_PORT}", jvmGatewayPort + "")
            .replace("${JVM_GATEWAY_ADDRESS}", serverAddress)).build());
    if (response.getStatus() == ExecuteStatus.ERROR) {
      throw new IOException("Fail to setup JVMGateway\n" + response.getOutput());
    }

    input =
        getClass().getClassLoader().getResourceAsStream("python/zeppelin_context.py");
    lines = IOUtils.readLines(input);
    response = ipythonClient.block_execute(ExecuteRequest.newBuilder()
        .setCode(StringUtils.join(lines, System.lineSeparator())).build());
    if (response.getStatus() == ExecuteStatus.ERROR) {
      throw new IOException("Fail to import ZeppelinContext\n" + response.getOutput());
    }

    response = ipythonClient.block_execute(ExecuteRequest.newBuilder()
        .setCode("z = __zeppelin__ = PyZeppelinContext(intp.getZeppelinContext(), gateway)")
        .build());
    if (response.getStatus() == ExecuteStatus.ERROR) {
      throw new IOException("Fail to setup ZeppelinContext\n" + response.getOutput());
    }

    if (additionalPythonInitFile != null) {
      input = getClass().getClassLoader().getResourceAsStream(additionalPythonInitFile);
      lines = IOUtils.readLines(input);
      response = ipythonClient.block_execute(ExecuteRequest.newBuilder()
          .setCode(StringUtils.join(lines, System.lineSeparator())
              .replace("${JVM_GATEWAY_PORT}", jvmGatewayPort + "")
              .replace("${JVM_GATEWAY_ADDRESS}", serverAddress)).build());
      if (response.getStatus() == ExecuteStatus.ERROR) {
        throw new IOException("Fail to run additional Python init file: "
            + additionalPythonInitFile + "\n" + response.getOutput());
      }
    }
  }

  private void launchIPythonKernel(int ipythonPort)
      throws IOException {
    LOGGER.info("Launching IPython Kernel at port: " + ipythonPort);
    // copy the python scripts to a temp directory, then launch ipython kernel in that folder
    File pythonWorkDir = Files.createTempDirectory("zeppelin_ipython").toFile();
    String[] ipythonScripts = {"ipython_server.py", "ipython_pb2.py", "ipython_pb2_grpc.py"};
    for (String ipythonScript : ipythonScripts) {
      URL url = getClass().getClassLoader().getResource("grpc/python"
          + "/" + ipythonScript);
      FileUtils.copyURLToFile(url, new File(pythonWorkDir, ipythonScript));
    }

    CommandLine cmd = CommandLine.parse(pythonExecutable);
    cmd.addArgument(pythonWorkDir.getAbsolutePath() + "/ipython_server.py");
    cmd.addArgument(ipythonPort + "");

    if (useBuiltinPy4j) {
      //TODO(zjffdu) don't do hard code on py4j here
      File py4jDestFile = new File(pythonWorkDir, "py4j-src-0.10.7.zip");
      FileUtils.copyURLToFile(getClass().getClassLoader().getResource(
          "python/py4j-src-0.10.7.zip"), py4jDestFile);
      if (additionalPythonPath != null) {
        // put the py4j at the end, because additionalPythonPath may already contain py4j.
        // e.g. PySparkInterpreter
        additionalPythonPath = additionalPythonPath + ":" + py4jDestFile.getAbsolutePath();
      } else {
        additionalPythonPath = py4jDestFile.getAbsolutePath();
      }
    }

    Map<String, String> envs = setupIPythonEnv();
    iPythonProcessLauncher = new IPythonProcessLauncher(cmd, envs);
    iPythonProcessLauncher.launch();
    iPythonProcessLauncher.waitForReady(ipythonLaunchTimeout);

    if (iPythonProcessLauncher.isLaunchTimeout()) {
      throw new IOException("Fail to launch IPython Kernel in " + ipythonLaunchTimeout / 1000
              + " seconds.\n" + iPythonProcessLauncher.getErrorMessage());
    }
    if (!iPythonProcessLauncher.isRunning()) {
      throw new IOException("Fail to launch IPython Kernel as the python process is failed.\n"
              + iPythonProcessLauncher.getErrorMessage());
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
    if (usePy4JAuth) {
      envs.put("PY4J_GATEWAY_SECRET", secret);
    }
    LOGGER.info("PYTHONPATH:" + envs.get("PYTHONPATH"));
    return envs;
  }

  @VisibleForTesting
  public IPythonProcessLauncher getIPythonProcessLauncher() {
    return iPythonProcessLauncher;
  }

  @Override
  public void close() throws InterpreterException {
    if (iPythonProcessLauncher != null) {
      LOGGER.info("Kill IPython Process");
      if (iPythonProcessLauncher.isRunning()) {
        ipythonClient.stop(StopRequest.newBuilder().build());
        try {
          ipythonClient.shutdown();
        } catch (InterruptedException e) {
          LOGGER.warn("Exception happens when shutting down ipythonClient", e);
        }
      }
      iPythonProcessLauncher.stop();
      iPythonProcessLauncher = null;
    }
    if (gatewayServer != null) {
      LOGGER.info("Shutdown Py4j GatewayServer");
      gatewayServer.shutdown();
      gatewayServer = null;
    }
  }

  @Override
  public InterpreterResult interpret(String st,
                                     InterpreterContext context) throws InterpreterException {
    zeppelinContext.setGui(context.getGui());
    zeppelinContext.setNoteGui(context.getNoteGui());
    zeppelinContext.setInterpreterContext(context);
    interpreterOutput.setInterpreterOutput(context.out);
    try {
      ExecuteResponse response =
              ipythonClient.stream_execute(ExecuteRequest.newBuilder().setCode(st).build(),
                      interpreterOutput);
      interpreterOutput.getInterpreterOutput().flush();
      // It is not known which method is called first (ipythonClient.stream_execute
      // or onProcessFailed) when ipython kernel process is exited. Because they are in
      // 2 different threads. So here we would check ipythonClient's status and sleep 1 second
      // if ipython kernel is maybe terminated.
      if (iPythonProcessLauncher.isRunning() && !ipythonClient.isMaybeIPythonFailed()) {
        return new InterpreterResult(
                InterpreterResult.Code.valueOf(response.getStatus().name()));
      } else {
        if (ipythonClient.isMaybeIPythonFailed()) {
          Thread.sleep(1000);
        }
        if (iPythonProcessLauncher.isRunning()) {
          return new InterpreterResult(
                  InterpreterResult.Code.valueOf(response.getStatus().name()));
        } else {
          return new InterpreterResult(InterpreterResult.Code.ERROR,
                  "IPython kernel is abnormally exited, please check your code and log.");
        }
      }
    } catch (Exception e) {
      throw new InterpreterException("Fail to interpret python code", e);
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    ipythonClient.cancel(CancelRequest.newBuilder().build());
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
                                                InterpreterContext interpreterContext) {
    LOGGER.debug("Call completion for: " + buf);
    List<InterpreterCompletion> completions = new ArrayList<>();
    CompletionResponse response =
        ipythonClient.complete(
            CompletionRequest.getDefaultInstance().newBuilder().setCode(buf)
                .setCursor(cursor).build());
    for (int i = 0; i < response.getMatchesCount(); i++) {
      String match = response.getMatches(i);
      int lastIndexOfDot = match.lastIndexOf(".");
      if (lastIndexOfDot != -1) {
        match = match.substring(lastIndexOfDot + 1);
      }
      completions.add(new InterpreterCompletion(match, match, ""));
    }
    return completions;
  }

  public BaseZeppelinContext getZeppelinContext() {
    return zeppelinContext;
  }

  class IPythonProcessLauncher extends ProcessLauncher {

    IPythonProcessLauncher(CommandLine commandLine,
                           Map<String, String> envs) {
      super(commandLine, envs);
    }

    @Override
    public void waitForReady(int timeout) {
      // wait until IPython kernel is started or timeout
      long startTime = System.currentTimeMillis();
      while (state == State.LAUNCHED) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          LOGGER.error("Interrupted by something", e);
        }

        try {
          StatusResponse response = ipythonClient.status(StatusRequest.newBuilder().build());
          if (response.getStatus() == IPythonStatus.RUNNING) {
            LOGGER.info("IPython Kernel is Running");
            onProcessRunning();
            break;
          } else {
            LOGGER.info("Wait for IPython Kernel to be started");
          }
        } catch (Exception e) {
          // ignore the exception, because is may happen when grpc server has not started yet.
          LOGGER.info("Wait for IPython Kernel to be started");
        }

        if ((System.currentTimeMillis() - startTime) > timeout) {
          onTimeout();
          break;
        }
      }
    }
  }
}
