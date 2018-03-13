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

package org.apache.zeppelin.scalding;

import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.twitter.scalding.ScaldingILoop;

import scala.Console;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

/**
 * Scalding interpreter for Zeppelin. Based off the Spark interpreter code.
 *
 */
public class ScaldingInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(ScaldingInterpreter.class);

  static final String ARGS_STRING = "args.string";
  static final String ARGS_STRING_DEFAULT = "--local --repl";
  static final String MAX_OPEN_INSTANCES = "max.open.instances";
  static final String MAX_OPEN_INSTANCES_DEFAULT = "50";

  public static final List NO_COMPLETION = Collections.unmodifiableList(new ArrayList<>());

  static int numOpenInstances = 0;
  private ScaldingILoop interpreter;
  private ByteArrayOutputStream out;

  public ScaldingInterpreter(Properties property) {
    super(property);
    out = new ByteArrayOutputStream();
  }

  @Override
  public void open() {
    numOpenInstances = numOpenInstances + 1;
    String maxOpenInstancesStr = getProperty(MAX_OPEN_INSTANCES,
            MAX_OPEN_INSTANCES_DEFAULT);
    int maxOpenInstances = 50;
    try {
      maxOpenInstances = Integer.valueOf(maxOpenInstancesStr);
    } catch (Exception e) {
      logger.error("Error reading max.open.instances", e);
    }
    logger.info("max.open.instances = {}", maxOpenInstances);
    if (numOpenInstances > maxOpenInstances) {
      logger.error("Reached maximum number of open instances");
      return;
    }
    logger.info("Opening instance {}", numOpenInstances);
    logger.info("property: {}", getProperties());
    String argsString = getProperty(ARGS_STRING, ARGS_STRING_DEFAULT);
    String[] args;
    if (argsString == null) {
      args = new String[0];
    } else {
      args = argsString.split(" ");
    }
    logger.info("{}", Arrays.toString(args));

    PrintWriter printWriter = new PrintWriter(out, true);
    interpreter = ZeppelinScaldingShell.getRepl(args, printWriter);
    interpreter.createInterpreter();
  }

  @Override
  public void close() {
    interpreter.intp().close();
  }


  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    String user = contextInterpreter.getAuthenticationInfo().getUser();
    logger.info("Running Scalding command: user: {} cmd: '{}'", user, cmd);

    if (interpreter == null) {
      logger.error(
          "interpreter == null, open may not have been called because max.open.instances reached");
      return new InterpreterResult(Code.ERROR,
        "interpreter == null\n" +
        "open may not have been called because max.open.instances reached"
      );
    }
    if (cmd == null || cmd.trim().length() == 0) {
      return new InterpreterResult(Code.SUCCESS);
    }
    InterpreterResult interpreterResult = new InterpreterResult(Code.ERROR);
    if (getProperty(ARGS_STRING).contains("hdfs")) {
      UserGroupInformation ugi = null;
      try {
        ugi = UserGroupInformation.createProxyUser(user, UserGroupInformation.getLoginUser());
      } catch (IOException e) {
        logger.error("Error creating UserGroupInformation", e);
        return new InterpreterResult(Code.ERROR, e.getMessage());
      }
      try {
        // Make variables final to avoid "local variable is accessed from within inner class;
        // needs to be declared final" exception in JDK7
        final String cmd1 = cmd;
        final InterpreterContext contextInterpreter1 = contextInterpreter;
        PrivilegedExceptionAction<InterpreterResult> action =
            new PrivilegedExceptionAction<InterpreterResult>() {
              public InterpreterResult run() throws Exception {
                return interpret(cmd1.split("\n"), contextInterpreter1);
              }
            };
        interpreterResult = ugi.doAs(action);
      } catch (Exception e) {
        logger.error("Error running command with ugi.doAs", e);
        return new InterpreterResult(Code.ERROR, e.getMessage());
      }
    } else {
      interpreterResult = interpret(cmd.split("\n"), contextInterpreter);
    }
    return interpreterResult;
  }

  public InterpreterResult interpret(String[] lines, InterpreterContext context) {
    synchronized (this) {
      InterpreterResult r = interpretInput(lines);
      return r;
    }
  }

  public InterpreterResult interpretInput(String[] lines) {

    // add print("") to make sure not finishing with comment
    // see https://github.com/NFLabs/zeppelin/issues/151
    String[] linesToRun = new String[lines.length + 1];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }
    linesToRun[lines.length] = "print(\"\")";

    out.reset();

    // Moving two lines below from open() to this function.
    // If they are in open output is incomplete.
    PrintStream printStream = new PrintStream(out, true);
    Console.setOut(printStream);

    Code r = null;
    String incomplete = "";
    boolean inComment = false;

    for (int l = 0; l < linesToRun.length; l++) {
      String s = linesToRun[l];
      // check if next line starts with "." (but not ".." or "./") it is treated as an invocation
      if (l + 1 < linesToRun.length) {
        String nextLine = linesToRun[l + 1].trim();
        boolean continuation = false;
        if (nextLine.isEmpty()
                || nextLine.startsWith("//")         // skip empty line or comment
                || nextLine.startsWith("}")
                || nextLine.startsWith("object")) { // include "} object" for Scala companion object
          continuation = true;
        } else if (!inComment && nextLine.startsWith("/*")) {
          inComment = true;
          continuation = true;
        } else if (inComment && nextLine.lastIndexOf("*/") >= 0) {
          inComment = false;
          continuation = true;
        } else if (nextLine.length() > 1
                && nextLine.charAt(0) == '.'
                && nextLine.charAt(1) != '.'     // ".."
                && nextLine.charAt(1) != '/') {  // "./"
          continuation = true;
        } else if (inComment) {
          continuation = true;
        }
        if (continuation) {
          incomplete += s + "\n";
          continue;
        }
      }

      scala.tools.nsc.interpreter.Results.Result res = null;
      try {
        res = interpreter.intp().interpret(incomplete + s);
      } catch (Exception e) {
        logger.error("Interpreter exception: ", e);
        return new InterpreterResult(Code.ERROR, e.getMessage());
      }

      r = getResultCode(res);

      if (r == Code.ERROR) {
        Console.flush();
        return new InterpreterResult(r, out.toString());
      } else if (r == Code.INCOMPLETE) {
        incomplete += s + "\n";
      } else {
        incomplete = "";
      }
    }
    if (r == Code.INCOMPLETE) {
      return new InterpreterResult(r, "Incomplete expression");
    } else {
      Console.flush();
      return new InterpreterResult(r, out.toString());
    }
  }

  private Code getResultCode(scala.tools.nsc.interpreter.Results.Result r) {
    if (r instanceof scala.tools.nsc.interpreter.Results.Success$) {
      return Code.SUCCESS;
    } else if (r instanceof scala.tools.nsc.interpreter.Results.Incomplete$) {
      return Code.INCOMPLETE;
    } else {
      return Code.ERROR;
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    // not implemented
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    // fine-grained progress not implemented - return 0
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        ScaldingInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    return NO_COMPLETION;
  }

}
