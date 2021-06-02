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

import io.grpc.ManagedChannelBuilder;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.jupyter.proto.CancelRequest;
import org.apache.zeppelin.interpreter.jupyter.proto.CompletionRequest;
import org.apache.zeppelin.interpreter.jupyter.proto.CompletionResponse;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteRequest;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteResponse;
import org.apache.zeppelin.interpreter.jupyter.proto.KernelStatus;
import org.apache.zeppelin.interpreter.jupyter.proto.StatusRequest;
import org.apache.zeppelin.interpreter.jupyter.proto.StatusResponse;
import org.apache.zeppelin.interpreter.jupyter.proto.StopRequest;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.interpreter.util.ProcessLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Jupyter Kernel Interpreter for Zeppelin. One instance of this class represents one
 * Jupyter Kernel. You can enhance the jupyter kernel by extending this class.
 * e.g. IPythonInterpreter.
 */
public class JupyterKernelInterpreter extends AbstractInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(JupyterKernelInterpreter.class);

  private JupyterKernelProcessLauncher jupyterKernelProcessLauncher;
  protected JupyterKernelClient jupyterKernelClient;
  protected ZeppelinContext z;

  private String kernel;
  // working directory of jupyter kernel
  protected File kernelWorkDir;
  // python executable file for launching the jupyter kernel
  protected String pythonExecutable;
  protected String condaEnv;
  private int kernelLaunchTimeout;

  private InterpreterOutputStream interpreterOutput = new InterpreterOutputStream(LOGGER);

  public JupyterKernelInterpreter(String kernel, Properties properties) {
    this(properties);
    this.kernel = kernel;
  }

  public JupyterKernelInterpreter(Properties properties) {
    super(properties);
  }

  public String getKernelName() {
    return this.kernel;
  }

  public List<String> getRequiredPackages() {
    List<String> requiredPackages = new ArrayList<>();
    requiredPackages.add("jupyter-client");
    requiredPackages.add("grpcio");
    requiredPackages.add("protobuf");
    return requiredPackages;
  }

  protected ZeppelinContext buildZeppelinContext() {
    return new JupyterZeppelinContext(null, 1000);
  }

  @Override
  public void open() throws InterpreterException {
    try {
      if (jupyterKernelClient != null) {
        // JupyterKernelInterpreter might already been opened
        return;
      }

      String envName = getProperty("zeppelin.interpreter.conda.env.name");
      if (StringUtils.isNotBlank(envName)) {
        activateCondaEnv(envName);
      } else {
        pythonExecutable = getProperty("zeppelin.python", "python");
      }

      LOGGER.info("Python Exec: {}", pythonExecutable);
      String checkPrerequisiteResult = checkKernelPrerequisite(pythonExecutable);
      if (!StringUtils.isEmpty(checkPrerequisiteResult)) {
        throw new InterpreterException("Kernel prerequisite is not meet: " +
                checkPrerequisiteResult);
      }
      kernelLaunchTimeout = Integer.parseInt(
              getProperty("zeppelin.jupyter.kernel.launch.timeout", "30000"));
      this.z = buildZeppelinContext();
      int kernelPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      int messageSize = Integer.parseInt(getProperty("zeppelin.jupyter.kernel.grpc.message_size",
              32 * 1024 * 1024 + ""));

      jupyterKernelClient = new JupyterKernelClient(ManagedChannelBuilder.forAddress("127.0.0.1",
              kernelPort).usePlaintext(true).maxInboundMessageSize(messageSize),
              getProperties(), kernel);
      launchJupyterKernel(kernelPort);
    } catch (Exception e) {
      throw new InterpreterException("Fail to open JupyterKernelInterpreter:\n" +
              ExceptionUtils.getStackTrace(e), e);
    }
  }

  /**
   * non-empty return value mean the errors when checking kernel prerequisite.
   * empty value mean kernel prerequisite is met.
   *
   * @return check result of checking kernel prerequisite.
   */
  public String checkKernelPrerequisite(String pythonExec) {
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
          return "Fail to run pip freeze.\n" + IOUtils.toString(in, StandardCharsets.UTF_8);
        }
      }
      try (FileInputStream in = new FileInputStream(stdoutFile)) {
        String freezeOutput = IOUtils.toString(in, StandardCharsets.UTF_8);
        for (String packageName : getRequiredPackages()) {
          /**
           * Example line, if the Python package is installed with pip:
           *  grpcio==1.18.0
           * Example line, if the Python package is installed with conda:
           *  grpcio @ file:///home/conda/feedstock_root/build_artifacts/grpcio_1604365513151/work
           */
          if (!freezeOutput.contains(packageName + "=") &&
              !freezeOutput.contains(packageName + " ")) {
            return packageName + " is not installed, installed packages:\n" + freezeOutput;
          }
        }
        LOGGER.info("Prerequisite for kernel {} is met", getKernelName());
      }
    } catch (Exception e) {
      LOGGER.warn("Fail to checkKernelPrerequisite", e);
      return "Fail to checkKernelPrerequisite: " + ExceptionUtils.getStackTrace(e);
    } finally {
      FileUtils.deleteQuietly(stderrFile);
      FileUtils.deleteQuietly(stdoutFile);
    }
    return "";
  }

  private void activateCondaEnv(String envName) throws IOException {
    LOGGER.info("Activating conda env: {}", envName);
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    PumpStreamHandler psh = new PumpStreamHandler(stdout);

    if (!new File(envName).exists()) {
      throw new IOException("Fail to activating conda env because no environment folder: " +
              envName);
    }
    File scriptFile = Files.createTempFile("zeppelin_jupyter_kernel_", ".sh").toFile();
    try (FileWriter writer = new FileWriter(scriptFile)) {
      IOUtils.write(String.format("chmod 777 -R %s \nsource %s/bin/activate \nconda-unpack",
              envName, envName),
              writer);
    }
    scriptFile.setExecutable(true, false);
    scriptFile.setReadable(true, false);
    CommandLine cmd = new CommandLine(scriptFile.getAbsolutePath());
    DefaultExecutor executor = new DefaultExecutor();
    executor.setStreamHandler(psh);
    int exitCode = executor.execute(cmd);
    if (exitCode != 0) {
      throw new IOException("Fail to activate conda env, " + stdout.toString());
    } else {
      LOGGER.info("Activate conda env successfully");
      this.condaEnv = envName;
      this.pythonExecutable = envName + "/bin/python";
    }
  }

  private void launchJupyterKernel(int kernelPort)
          throws IOException {

    LOGGER.info("Launching Jupyter Kernel at port: {}", kernelPort);
    // copy the python scripts to a temp directory, then launch jupyter kernel in that folder
    this.kernelWorkDir = Files.createTempDirectory(
            "zeppelin_jupyter_kernel_" + getKernelName()).toFile();
    String[] kernelScripts = {"kernel_server.py", "kernel_pb2.py", "kernel_pb2_grpc.py"};
    for (String kernelScript : kernelScripts) {
      URL url = getClass().getClassLoader().getResource("grpc/jupyter"
              + "/" + kernelScript);
      FileUtils.copyURLToFile(url, new File(kernelWorkDir, kernelScript));
    }

    File bootstrapScriptFile = buildBootstrapScriptFile(kernelPort);
    LOGGER.info("Bootstrap script file: " + bootstrapScriptFile.getAbsolutePath());
    CommandLine cmd = CommandLine.parse(bootstrapScriptFile.getAbsolutePath());
    Map<String, String> envs = setupKernelEnv();
    jupyterKernelProcessLauncher = new JupyterKernelProcessLauncher(cmd, envs);
    jupyterKernelProcessLauncher.launch();
    jupyterKernelProcessLauncher.waitForReady(kernelLaunchTimeout);

    if (jupyterKernelProcessLauncher.isLaunchTimeout()) {
      throw new IOException("Fail to launch Jupyter Kernel in " + kernelLaunchTimeout / 1000
              + " seconds.\n" + jupyterKernelProcessLauncher.getErrorMessage());
    }
    if (!jupyterKernelProcessLauncher.isRunning()) {
      throw new IOException("Fail to launch Jupyter Kernel as the python process is failed.\n"
              + jupyterKernelProcessLauncher.getErrorMessage());
    }
  }

  private File buildBootstrapScriptFile(int kernelPort) throws IOException {
    StringBuilder builder = new StringBuilder();
    if (condaEnv != null) {
      builder.append("source " + condaEnv + "/bin/activate\n");
    }
    builder.append(pythonExecutable);
    builder.append(" " + kernelWorkDir.getAbsolutePath() + "/kernel_server.py");
    builder.append(" " + getKernelName());
    builder.append(" " + kernelPort);
    File scriptFile = Files.createTempFile("zeppelin_jupyter_", ".sh").toFile();
    try (FileWriter out = new FileWriter(scriptFile)) {
      IOUtils.write(builder.toString(), out);
    }
    scriptFile.setExecutable(true);
    return scriptFile;
  }

  protected Map<String, String> setupKernelEnv() throws IOException {
    return EnvironmentUtils.getProcEnvironment();
  }

  public JupyterKernelProcessLauncher getKernelProcessLauncher() {
    return jupyterKernelProcessLauncher;
  }

  @Override
  public void close() throws InterpreterException {
    if (jupyterKernelProcessLauncher != null) {
      LOGGER.info("Shutdown Jupyter Kernel Process");
      if (jupyterKernelProcessLauncher.isRunning()) {
        jupyterKernelClient.stop(StopRequest.newBuilder().build());
        try {
          jupyterKernelClient.shutdown();
        } catch (InterruptedException e) {
          LOGGER.warn("Exception happens when shutting down jupyter kernel client", e);
        }
      }
      jupyterKernelProcessLauncher.stop();
      jupyterKernelProcessLauncher = null;
      LOGGER.info("Jupyter Kernel is killed");
    }
  }

  @Override
  public InterpreterResult internalInterpret(String st,
                                     InterpreterContext context) throws InterpreterException {
    z.setGui(context.getGui());
    z.setNoteGui(context.getNoteGui());
    z.setInterpreterContext(context);
    interpreterOutput.setInterpreterOutput(context.out);
    jupyterKernelClient.setInterpreterContext(context);
    try {
      ExecuteResponse response =
              jupyterKernelClient.stream_execute(ExecuteRequest.newBuilder().setCode(st).build(),
                      interpreterOutput);
      interpreterOutput.getInterpreterOutput().flush();
      // It is not known which method is called first (JupyterKernelClient.stream_execute
      // or onProcessFailed) when jupyter kernel process is exited. Because they are in
      // 2 different threads. So here we would check JupyterKernelClient's status and sleep 1 second
      // if jupyter kernel is maybe terminated.
      if (jupyterKernelProcessLauncher.isRunning() && !jupyterKernelClient.isMaybeKernelFailed()) {
        return new InterpreterResult(
                InterpreterResult.Code.valueOf(response.getStatus().name()));
      } else {
        if (jupyterKernelClient.isMaybeKernelFailed()) {
          Thread.sleep(1000);
        }
        if (jupyterKernelProcessLauncher.isRunning()) {
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
    jupyterKernelClient.cancel(CancelRequest.newBuilder().build());
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
    LOGGER.debug("Call completion for: {}, cursor: {}", buf, cursor);
    List<InterpreterCompletion> completions = new ArrayList<>();
    CompletionResponse response =
            jupyterKernelClient.complete(
                    CompletionRequest.getDefaultInstance().newBuilder().setCode(buf)
                            .setCursor(cursor).build());
    for (int i = 0; i < response.getMatchesCount(); i++) {
      String match = response.getMatches(i);
      int lastIndexOfDot = match.lastIndexOf(".");
      if (lastIndexOfDot != -1) {
        match = match.substring(lastIndexOfDot + 1);
      }
      LOGGER.debug("Candidate completion: {}", match);
      completions.add(new InterpreterCompletion(match, match, ""));
    }
    return completions;
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return z;
  }

  public class JupyterKernelProcessLauncher extends ProcessLauncher {

    JupyterKernelProcessLauncher(CommandLine commandLine,
                                 Map<String, String> envs) {
      super(commandLine, envs);
    }

    @Override
    public void waitForReady(int timeout) {
      // wait until jupyter kernel is started or timeout
      long startTime = System.currentTimeMillis();
      while (state == State.LAUNCHED) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          LOGGER.error("Interrupted by something", e);
        }

        try {
          StatusResponse response = jupyterKernelClient.status(StatusRequest.newBuilder().build());
          if (response.getStatus() == KernelStatus.RUNNING) {
            LOGGER.info("Jupyter Kernel is Running");
            onProcessRunning();
            break;
          } else {
            LOGGER.info("Wait for Jupyter Kernel to be started");
          }
        } catch (Exception e) {
          // ignore the exception, because is may happen when grpc server has not started yet.
          LOGGER.info("Wait for Jupyter Kernel to be started");
        }

        if ((System.currentTimeMillis() - startTime) > timeout) {
          onTimeout();
          break;
        }
      }
    }
  }
}
