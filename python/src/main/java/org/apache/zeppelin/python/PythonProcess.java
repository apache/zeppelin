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
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.lang.reflect.Field;

/**
 * Object encapsulated interactive
 * Python process (REPL) used by python interpreter
 */
public class PythonProcess {
  Logger logger = LoggerFactory.getLogger(PythonProcess.class);

  InputStream stdout;
  OutputStream stdin;
  BufferedWriter writer;
  BufferedReader reader;
  Process process;

  private String binPath;
  private long pid;

  public PythonProcess(String binPath) {
    this.binPath = binPath;
  }

  public void open() throws IOException {
    ProcessBuilder builder = new ProcessBuilder(binPath, "-iu");

    builder.redirectErrorStream(true);
    process = builder.start();
    stdout = process.getInputStream();
    stdin = process.getOutputStream();
    writer = new BufferedWriter(new OutputStreamWriter(stdin));
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
    writer.write(cmd + "\n\n");
    writer.write("print (\"*!?flush reader!?*\")\n\n");
    writer.flush();

    String output = "";
    String line;
    while (!(line = reader.readLine()).contains("*!?flush reader!?*")) {
      logger.debug("Readed line from python shell : " + line);
      if (line.equals("...")) {
        logger.warn("Syntax error ! ");
        output += "Syntax error ! ";
        break;
      }
      output += "\r" + line + "\n";
    }
    return output;
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
