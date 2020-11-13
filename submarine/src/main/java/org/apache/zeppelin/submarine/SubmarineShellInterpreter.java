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

package org.apache.zeppelin.submarine;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.shell.ShellInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_ALGORITHM_HDFS_PATH;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_HADOOP_KEYTAB;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_HADOOP_PRINCIPAL;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_CHECKPOINT_PATH;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.USERNAME_SYMBOL;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.ZEPPELIN_SUBMARINE_AUTH_TYPE;

/**
 * Submarine Shell interpreter for Zeppelin.
 */
public class SubmarineShellInterpreter extends ShellInterpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(SubmarineShellInterpreter.class);

  private final boolean isWindows = System.getProperty("os.name").startsWith("Windows");
  private final String shell = isWindows ? "cmd /c" : "bash -c";

  public SubmarineShellInterpreter(Properties property) {
    super(property);
  }

  @Override
  public InterpreterResult internalInterpret(String cmd, InterpreterContext context) {
    setParagraphConfig(context);

    // algorithm path & checkpoint path support replaces ${username} with real user name
    String algorithmPath = properties.getProperty(SUBMARINE_ALGORITHM_HDFS_PATH, "");
    if (algorithmPath.contains(USERNAME_SYMBOL)) {
      algorithmPath = algorithmPath.replace(USERNAME_SYMBOL, userName);
      properties.setProperty(SUBMARINE_ALGORITHM_HDFS_PATH, algorithmPath);
    }
    String checkpointPath = properties.getProperty(
        TF_CHECKPOINT_PATH, "");
    if (checkpointPath.contains(USERNAME_SYMBOL)) {
      checkpointPath = checkpointPath.replace(USERNAME_SYMBOL, userName);
      properties.setProperty(TF_CHECKPOINT_PATH, checkpointPath);
    }

    return super.internalInterpret(cmd, context);
  }

  private void setParagraphConfig(InterpreterContext context) {
    context.getConfig().put("editorHide", false);
    context.getConfig().put("title", true);
  }

  @Override
  protected boolean runKerberosLogin() {
    try {
      createSecureConfiguration();
      return true;
    } catch (Exception e) {
      LOGGER.error("Unable to run kinit for zeppelin", e);
    }
    return false;
  }

  public void createSecureConfiguration() throws InterpreterException {
    Properties properties = getProperties();
    CommandLine cmdLine = CommandLine.parse(shell);
    cmdLine.addArgument("-c", false);
    String kinitCommand = String.format("kinit -k -t %s %s",
        properties.getProperty(SUBMARINE_HADOOP_KEYTAB),
        properties.getProperty(SUBMARINE_HADOOP_PRINCIPAL));
    cmdLine.addArgument(kinitCommand, false);
    DefaultExecutor executor = new DefaultExecutor();
    try {
      executor.execute(cmdLine);
    } catch (Exception e) {
      LOGGER.error("Unable to run kinit for zeppelin user " + kinitCommand, e);
      throw new InterpreterException(e);
    }
  }

  @Override
  protected boolean isKerboseEnabled() {
    String authType = getProperty(ZEPPELIN_SUBMARINE_AUTH_TYPE, "");
    if (StringUtils.equals(authType, "kerberos")) {
      return true;
    }
    return false;
  }
}
