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
import com.google.gson.reflect.TypeToken;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.Map;

public class Kubectl {
  private final String kubectlCmd;
  private final Gson gson = new Gson();

  public Kubectl(String kubectlCmd) {
    this.kubectlCmd = kubectlCmd;
  }

  public Map<String, Object> apply(String spec) throws IOException {
    return execAndGetJson(new String[]{"apply", "-o", "json", "-f", "-"}, spec);
  }

  public Map<String, Object> delete(String spec) throws IOException {
    return execAndGetJson(new String[]{"delete", "-o", "json", "-f", "-"}, spec);
  }

  Map<String, Object> execAndGetJson(String [] args) throws IOException {
    return execAndGetJson(args, "");
  }

  @VisibleForTesting
  Map<String, Object> execAndGetJson(String [] args, String stdin) throws IOException {
    InputStream ins = IOUtils.toInputStream(stdin);
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    int exitCode = execute(
        args,
        ins,
        stdout,
        stderr
    );

    if (exitCode == 0) {
      String output = new String(stdout.toByteArray());
      return gson.fromJson(output, new TypeToken<Map<String, Object>>() {}.getType());
    } else {
      throw new IOException(String.format("non zero return code (%d)", exitCode));
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
