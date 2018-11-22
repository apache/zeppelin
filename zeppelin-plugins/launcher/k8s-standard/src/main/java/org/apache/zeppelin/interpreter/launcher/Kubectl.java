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

package org.apache.zeppelin.interpreter.launcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.exec.*;
import org.apache.commons.io.IOUtils;

import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kubectl {
  private final Logger logger = LoggerFactory.getLogger(Kubectl.class);
  private final String kubectlCmd;
  private final Gson gson = new Gson();
  private String namespace;

  public Kubectl(String kubectlCmd) {
    this.kubectlCmd = kubectlCmd;
  }

  /**
   * Override namespace. Otherwise use namespace provided in schema
   * @param namespace
   */
  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getNamespace() {
    return namespace;
  }

  public String apply(String spec) throws IOException {
    return execAndGet(new String[]{"apply", "-f", "-"}, spec);
  }

  public String delete(String spec) throws IOException {
    return execAndGet(new String[]{"delete", "-f", "-"}, spec);
  }

  public String wait(String resource, String waitFor, int timeoutSec) throws IOException {
    return execAndGet(new String[]{
            "wait",
            resource,
            String.format("--for=%s", waitFor),
            String.format("--timeout=%ds", timeoutSec)});
  }

  public ExecuteWatchdog portForward(String resource, String [] ports) throws IOException {
    DefaultExecutor executor = new DefaultExecutor();
    CommandLine cmd = new CommandLine(kubectlCmd);
    cmd.addArguments("port-forward");
    cmd.addArguments(resource);
    cmd.addArguments(ports);

    ExecuteWatchdog watchdog = new ExecuteWatchdog(-1);
    executor.setWatchdog(watchdog);

    executor.execute(cmd, new ExecuteResultHandler() {
      @Override
      public void onProcessComplete(int i) {
        logger.info("Port-forward stopped");
      }

      @Override
      public void onProcessFailed(ExecuteException e) {
        logger.debug("port-forward process exit", e);
      }
    });

    return watchdog;
  }

  String execAndGet(String [] args) throws IOException {
    return execAndGet(args, "");
  }

  @VisibleForTesting
  String execAndGet(String [] args, String stdin) throws IOException {
    InputStream ins = IOUtils.toInputStream(stdin);
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    ArrayList<String> argsToOverride = new ArrayList<>(Arrays.asList(args));

    // set namespace
    if (namespace != null) {
      argsToOverride.add("--namespace=" + namespace);
    }

    logger.info("kubectl " + argsToOverride + "\n" + stdin);

    try {
      int exitCode = execute(
              argsToOverride.toArray(new String[0]),
              ins,
              stdout,
              stderr
      );

      if (exitCode == 0) {
        String output = new String(stdout.toByteArray());
        return output;
      } else {
        String output = new String(stderr.toByteArray());
        throw new IOException(String.format("non zero return code (%d). %s", exitCode, output));
      }
    } catch (Exception e) {
      String output = new String(stderr.toByteArray());
      throw new IOException(output, e);
    }
  }

  public int execute(String [] args, InputStream stdin, OutputStream stdout, OutputStream stderr) throws IOException {
    DefaultExecutor executor = new DefaultExecutor();
    CommandLine cmd = new CommandLine(kubectlCmd);
    cmd.addArguments(args);

    ExecuteWatchdog watchdog = new ExecuteWatchdog(60 * 1000);
    executor.setWatchdog(watchdog);

    PumpStreamHandler streamHandler = new PumpStreamHandler(stdout, stderr, stdin);
    executor.setStreamHandler(streamHandler);
    return executor.execute(cmd);
  }
}
