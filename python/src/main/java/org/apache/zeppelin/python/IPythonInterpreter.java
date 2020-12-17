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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteRequest;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteResponse;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteStatus;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.jupyter.JupyterKernelInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * IPython Interpreter for Zeppelin. It enhances the JupyterKernelInterpreter by setting up
 * communication between JVM and Python process via py4j. So that in IPythonInterpreter
 * you can use ZeppelinContext.
 */
public class IPythonInterpreter extends JupyterKernelInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IPythonInterpreter.class);

  // GatewayServer in jvm side to communicate with python kernel process.
  private GatewayServer gatewayServer;
  // allow to set PYTHONPATH
  private String additionalPythonPath;
  private String additionalPythonInitFile;
  private boolean useBuiltinPy4j = true;
  private boolean usePy4JAuth = true;
  private String py4jGatewaySecret;

  public IPythonInterpreter(Properties properties) {
    super("python", properties);
  }

  @Override
  public String getKernelName() {
    return "python";
  }

  @Override
  public List<String> getRequiredPackages() {
    List<String> requiredPackages = super.getRequiredPackages();
    requiredPackages.add("ipython");
    requiredPackages.add("ipykernel");
    return requiredPackages;
  }

  /**
   * Sub class can customize the interpreter by adding more python packages under PYTHONPATH.
   * e.g. IPySparkInterpreter
   *
   * @param additionalPythonPath
   */
  public void setAdditionalPythonPath(String additionalPythonPath) {
    this.additionalPythonPath = additionalPythonPath;
  }

  /**
   * Sub class can customize the interpreter by running additional python init code.
   * e.g. IPySparkInterpreter
   *
   * @param additionalPythonInitFile
   */
  public void setAdditionalPythonInitFile(String additionalPythonInitFile) {
    this.additionalPythonInitFile = additionalPythonInitFile;
  }

  public void setUseBuiltinPy4j(boolean useBuiltinPy4j) {
    this.useBuiltinPy4j = useBuiltinPy4j;
  }

  @Override
  public ZeppelinContext buildZeppelinContext() {
    return new PythonZeppelinContext(
        getInterpreterGroup().getInterpreterHookRegistry(),
        Integer.parseInt(getProperty("zeppelin.python.maxResult", "1000")));
  }

  @Override
  public void open() throws InterpreterException {
    super.open();
    try {
      String gatewayHost = PythonUtils.getLocalIP(properties);
      int gatewayPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      setupJVMGateway(gatewayHost, gatewayPort);
      initPythonInterpreter(gatewayHost, gatewayPort);
    } catch (Exception e) {
      LOGGER.error("Fail to open IPythonInterpreter", e);
      throw new InterpreterException(e);
    }
  }

  private void setupJVMGateway(String gatewayHost, int gatewayPort) throws IOException {
    this.gatewayServer = PythonUtils.createGatewayServer(this, gatewayHost,
            gatewayPort, py4jGatewaySecret, usePy4JAuth);
    gatewayServer.start();
  }

  private void initPythonInterpreter(String gatewayHost, int gatewayPort) throws IOException {
    InputStream input =
            getClass().getClassLoader().getResourceAsStream("python/zeppelin_ipython.py");
    List<String> lines = IOUtils.readLines(input, StandardCharsets.UTF_8);
    ExecuteResponse response = jupyterKernelClient.block_execute(ExecuteRequest.newBuilder()
            .setCode(StringUtils.join(lines, System.lineSeparator())
                    .replace("${JVM_GATEWAY_PORT}", gatewayPort + "")
                    .replace("${JVM_GATEWAY_ADDRESS}", gatewayHost)).build());
    if (response.getStatus() != ExecuteStatus.SUCCESS) {
      throw new IOException("Fail to setup JVMGateway\n" + response.getOutput());
    }

    input =
            getClass().getClassLoader().getResourceAsStream("python/zeppelin_context.py");
    lines = IOUtils.readLines(input, StandardCharsets.UTF_8);
    response = jupyterKernelClient.block_execute(ExecuteRequest.newBuilder()
            .setCode(StringUtils.join(lines, System.lineSeparator())).build());
    if (response.getStatus() != ExecuteStatus.SUCCESS) {
      throw new IOException("Fail to import ZeppelinContext\n" + response.getOutput());
    }

    response = jupyterKernelClient.block_execute(ExecuteRequest.newBuilder()
            .setCode("z = __zeppelin__ = PyZeppelinContext(intp.getZeppelinContext(), gateway)")
            .build());
    if (response.getStatus() != ExecuteStatus.SUCCESS) {
      throw new IOException("Fail to setup ZeppelinContext\n" + response.getOutput());
    }

    if (additionalPythonInitFile != null) {
      input = getClass().getClassLoader().getResourceAsStream(additionalPythonInitFile);
      lines = IOUtils.readLines(input, StandardCharsets.UTF_8);
      response = jupyterKernelClient.block_execute(ExecuteRequest.newBuilder()
              .setCode(StringUtils.join(lines, System.lineSeparator())
                      .replace("${JVM_GATEWAY_PORT}", gatewayPort + "")
                      .replace("${JVM_GATEWAY_ADDRESS}", gatewayHost)).build());
      if (response.getStatus() != ExecuteStatus.SUCCESS) {
        LOGGER.error("Fail to run additional Python init file\n{}", response.getOutput());
        throw new IOException("Fail to run additional Python init file: "
                + additionalPythonInitFile + "\n" + response.getOutput());
      }
    }
  }

  @Override
  protected Map<String, String> setupKernelEnv() throws IOException {
    Map<String, String> envs = super.setupKernelEnv();
    if (useBuiltinPy4j) {
      //TODO(zjffdu) don't do hard code on py4j here
      File py4jDestFile = new File(kernelWorkDir, "py4j-src-0.10.7.zip");
      FileUtils.copyURLToFile(getClass().getClassLoader().getResource(
              "python/py4j-src-0.10.7.zip"), py4jDestFile);
      if (additionalPythonPath != null) {
        // put the py4j at the end, because additionalPythonPath may already contain py4j.
        // e.g. IPySparkInterpreter
        additionalPythonPath = additionalPythonPath + ":" + py4jDestFile.getAbsolutePath();
      } else {
        additionalPythonPath = py4jDestFile.getAbsolutePath();
      }
    }
    if (envs.containsKey("PYTHONPATH")) {
      if (additionalPythonPath != null) {
        envs.put("PYTHONPATH", additionalPythonPath + ":" + envs.get("PYTHONPATH"));
      }
    } else {
      envs.put("PYTHONPATH", additionalPythonPath);
    }

    this.usePy4JAuth = Boolean.parseBoolean(getProperty("zeppelin.py4j.useAuth", "true"));
    this.py4jGatewaySecret = PythonUtils.createSecret(256);
    if (usePy4JAuth) {
      envs.put("PY4J_GATEWAY_SECRET", this.py4jGatewaySecret);
    }
    LOGGER.info("PYTHONPATH: {}", envs.get("PYTHONPATH"));
    return envs;
  }

  @Override
  public void close() throws InterpreterException {
    super.close();
    if (gatewayServer != null) {
      LOGGER.info("Shutdown Py4j GatewayServer");
      gatewayServer.shutdown();
      gatewayServer = null;
    }
  }
}
