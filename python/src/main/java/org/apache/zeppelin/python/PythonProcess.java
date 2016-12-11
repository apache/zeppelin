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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.lang.reflect.Field;

/**
 * Object encapsulated interactive
 * Python process (REPL) used by python interpreter
 */
public class PythonProcess {
  private static final Logger logger = LoggerFactory.getLogger(PythonProcess.class);
  private static final String STATEMENT_END = "*!?flush reader!?*";
  InputStream stdout;
  OutputStream stdin;
  PrintWriter writer;
  BufferedReader reader;
  Process process;

  private String binPath;
  private String pythonPath;
  private long pid;

  public PythonProcess(String binPath, String pythonPath) {
    this.binPath = binPath;
    this.pythonPath = pythonPath;
  }

  public void open() throws IOException {
    ProcessBuilder builder;
    boolean hasParams = binPath.split(" ").length > 1;
    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
      if (hasParams) {
        builder = new ProcessBuilder(binPath.split(" "));
      } else {
        builder = new ProcessBuilder(binPath, "-iu");
      }
    } else {
      String cmd;
      if (hasParams) {
        cmd = binPath;
      } else {
        cmd = binPath + " -iu";
      }
      builder = new ProcessBuilder("bash", "-c", cmd);
      if (pythonPath != null) {
        builder.environment().put("PYTHONPATH", pythonPath);
      }
    }

    builder.redirectErrorStream(true);
    process = builder.start();
    stdout = process.getInputStream();
    stdin = process.getOutputStream();
    writer = new PrintWriter(stdin, true);
    reader = new BufferedReader(new InputStreamReader(stdout));
    try {
      pid = findPid();
    } catch (Exception e) {
      logger.warn("Can't find python pid process", e);
      pid = -1;
    }
  }

  public void close() throws IOException {
    process.destroy();
    reader.close();
    writer.close();
    stdin.close();
    stdout.close();
  }

  public void interrupt() throws IOException {
    if (pid > -1) {
      logger.info("Sending SIGINT signal to PID : " + pid);
      Runtime.getRuntime().exec("kill -SIGINT " + pid);
    } else {
      logger.warn("Non UNIX/Linux system, close the interpreter");
      close();
    }
  }

  public String sendAndGetResult(String cmd) throws IOException {
    writer.println(cmd);
    writer.println();
    writer.println("\"" + STATEMENT_END + "\"");
    StringBuilder output = new StringBuilder();
    String line = null;
    while (!(line = reader.readLine()).contains(STATEMENT_END)) {
      logger.debug("Read line from python shell : " + line);
      output.append(line + "\n");
    }
    return output.toString();
  }

  private long findPid() throws NoSuchFieldException, IllegalAccessException {
    long pid = -1;
    if (process.getClass().getName().equals("java.lang.UNIXProcess")) {
      Field f = process.getClass().getDeclaredField("pid");
      f.setAccessible(true);
      pid = f.getLong(process);
      f.setAccessible(false);
    }
    return pid;
  }

  public long getPid() {
    return pid;
  }

}
