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

import java.io.*;
import java.util.*;
import java.net.URL;
import java.net.URLClassLoader;

import com.twitter.scalding.ScaldingILoop;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Console;
import scala.Some;
import scala.None;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;

/**
 * Scalding interpreter for Zeppelin. Based off the Spark interpreter code.
 *
 */
public class ScaldingInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(ScaldingInterpreter.class);

  static final String ARGS_STRING = "args.string";

  public static final List<String> NO_COMPLETION = 
    Collections.unmodifiableList(new ArrayList<String>());

  static {
    Interpreter.register(
      "scalding",
      "scalding",
      ScaldingInterpreter.class.getName(),
      new InterpreterPropertyBuilder()
        .add(ARGS_STRING, "--hdfs --repl", "Arguments for scalding REPL").build());
  }

  private ScaldingILoop interpreter;
  private ByteArrayOutputStream out;

  public ScaldingInterpreter(Properties property) {
    super(property);
    out = new ByteArrayOutputStream();
  }

  @Override
  public void open() {
    logger.info("property: {}", property);
    String argsString = property.getProperty(ARGS_STRING);
    String[] args;
    if (argsString == null) {
      args = new String[0];
    } else {
      args = argsString.split(" ");
    }
    logger.info("{}", Arrays.toString(args));

    PrintWriter printWriter = new PrintWriter(out, true);
    interpreter = com.twitter.scalding.ZeppelinScaldingShell.getRepl(args, printWriter);
    interpreter.createInterpreter();
  }

  @Override
  public void close() {
    interpreter.intp().close();
  }


  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    logger.info("Running Scalding command '" + cmd + "'");

    if (cmd == null || cmd.trim().length() == 0) {
      return new InterpreterResult(Code.SUCCESS);
    }
    return interpret(cmd.split("\n"), contextInterpreter);
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
  public List<String> completion(String buf, int cursor) {
    return NO_COMPLETION;
  }

}
