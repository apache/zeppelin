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

import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;

import java.io.*;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Python interpreter for Zeppelin.
 */
public class PythonInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(PythonInterpreter.class);

  public static final String BOOTSTRAP_PY = "/bootstrap.py";
  public static final String BOOTSTRAP_INPUT_PY = "/bootstrap_input.py";
  public static final String PYTHON_PATH = "python.path";
  public static final String DEFAULT_PYTHON_PATH = "/usr/bin/python";
  private String pythonPath;

  private Integer port;
  private GatewayServer gatewayServer;
  InputStream stdout;
  OutputStream stdin;
  BufferedWriter writer;
  BufferedReader reader;
  Process process = null;
  private long pythonPid;
  private Boolean py4J = false;
  private InterpreterContext context;

  static {
    Interpreter.register(
            "python",
            "python",
            PythonInterpreter.class.getName(),
            new InterpreterPropertyBuilder()
                    .add(PYTHON_PATH, DEFAULT_PYTHON_PATH,
                            "Python path. Default : /usr/bin/python")
                    .build()
    );
  }



  public PythonInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {

    logger.info("Starting Python interpreter .....");

    pythonPath = getProperty(PYTHON_PATH);

    logger.info("Python path is set to:" + pythonPath );

    ProcessBuilder builder = new ProcessBuilder(pythonPath, "-iu");

    builder.redirectErrorStream(true);

    try {
      process = builder.start();
    } catch (IOException e) {
      logger.error("Can't start python process", e);
    }

    pythonPid = getPidOfProcess(process);
    logger.info("python PID : " + pythonPid);

    stdout = process.getInputStream ();
    stdin = process.getOutputStream();
    writer = new BufferedWriter(new OutputStreamWriter(stdin));
    reader = new BufferedReader(new InputStreamReader(stdout));


    try {
      logger.info("Bootstrap interpreter with " + BOOTSTRAP_PY);
      bootStrapInterpreter(BOOTSTRAP_PY);
    } catch (IOException e) {
      logger.error("Can't execute " + BOOTSTRAP_PY + " to initiate python process", e);
    }

    if (py4J = isPy4jInstalled()){
      port = findRandomOpenPortOnAllLocalInterfaces();
      logger.info("Py4j gateway port : " + port);
      try {
        gatewayServer = new GatewayServer(this, port);
        gatewayServer.start();
        logger.info("Bootstrap inputs with " + BOOTSTRAP_INPUT_PY);
        bootStrapInterpreter(BOOTSTRAP_INPUT_PY);
      } catch (IOException e) {
        logger.error("Can't execute " + BOOTSTRAP_INPUT_PY + " to " +
                "initialize Zeppelin inputs in python process", e);
      }
    }


  }

  @Override
  public void close() {

    logger.info("closing Python interpreter .....");
    try {
      process.destroy();
      reader.close();
      writer.close();
      stdin.close();
      stdout.close();
    } catch (IOException e) {
      logger.error("Can't close the interpreter", e);
    }

  }


  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {

    this.context = contextInterpreter;

    String output = sendCommandToPython(cmd);
    return new InterpreterResult(Code.SUCCESS, output.replaceAll(">>>", "")
            .replaceAll("\\.\\.\\.", "").trim());
  }


  @Override
  public void cancel(InterpreterContext context) {
    if (pythonPid > -1) {
      try {
        logger.info("Sending SIGINT signal to PID : " + pythonPid);
        Runtime.getRuntime().exec("kill -SIGINT " + pythonPid);
      } catch (IOException e) {
        logger.error("Can't send SiGINT to PID : " + pythonPid, e);
      }
    }
    else {
      logger.warn("Non UNIX/Linux system, close the interpreter");
      close();
    }
  }
  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
            PythonInterpreter.class.getName() + this.hashCode(), 10);
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

  private Job getRunningJob(String paragraphId) {
    Job foundJob = null;
    Collection<Job> jobsRunning = getScheduler().getJobsRunning();
    for (Job job : jobsRunning) {
      if (job.getId().equals(paragraphId)) {
        foundJob = job;
      }
    }
    return foundJob;
  }


  private String sendCommandToPython(String cmd) {
    try {
      logger.info("Sending : \n " + cmd);
      writer.write(cmd + "\n\n");
      writer.write("print (\"*!?flush reader!?*\")\n\n");
      writer.flush();
    } catch (IOException e) {
      logger.error("Error when sending commands to python process stdin", e);
    }

    String output = "";
    String line;

    try {
      while (!(line = reader.readLine ()).contains("*!?flush reader!?*")){
        logger.info("Readed line from python shell : " + line);
        if (line.equals("...")) {
          logger.info("Syntax error ! ");
          output += "Syntax error ! ";
          break;
        }

        output += "\r" + line + "\n";
      }

    } catch (IOException e) {
      logger.error("Error when reading from python process stdout", e);
    }
    return output;
  }


  private void bootStrapInterpreter(String file) throws IOException {

    BufferedReader bootstrapReader = new BufferedReader(
            new InputStreamReader(
                    PythonInterpreter.class.getResourceAsStream(file)));
    String line = null;
    String bootstrapCode = "";
    while ((line = bootstrapReader.readLine()) != null)
    {
      bootstrapCode += line + "\n";
    }

    if (py4J && port != null && port != -1)
      bootstrapCode = bootstrapCode.replaceAll("\\%PORT\\%", port.toString());
    logger.info("Bootstrap python interpreter with \n " + bootstrapCode);
    writer.write(bootstrapCode);
    writer.flush();
  }

  private long getPidOfProcess(Process p) {
    long pid = -1;

    try {
      if (p.getClass().getName().equals("java.lang.UNIXProcess")) {
        Field f = p.getClass().getDeclaredField("pid");
        f.setAccessible(true);
        pid = f.getLong(p);
        f.setAccessible(false);
      }
    }
    catch (Exception e) {
      logger.warn("Can't find python pid process", e);
      pid = -1;
    }
    return pid;
  }

  public GUI getGui(){

    return context.getGui();

  }

  private Boolean isPy4jInstalled(){

    String output = sendCommandToPython("\n\nimport py4j\n");
    if (output.contains("ImportError"))
      return false;
    else return true;

  }

  private int findRandomOpenPortOnAllLocalInterfaces() {
    Integer port = -1;
    try (ServerSocket socket = new ServerSocket(0);) {
      port = socket.getLocalPort();
      socket.close();
    } catch (IOException e) {
      logger.error("Can't find an open port", e);
    }
    return port;
  }

}
