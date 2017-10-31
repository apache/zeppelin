/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.file;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * File interpreter for Zeppelin.
 *
 */
public abstract class FileInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(FileInterpreter.class);
  String currentDir = null;
  CommandArgs args = null;

  public FileInterpreter(Properties property) {
    super(property);
    currentDir = new String("/");
  }

  /**
   * Handling the arguments of the command
   */
  public class CommandArgs {
    public String input = null;
    public String command = null;
    public ArrayList<String> args = null;
    public HashSet<Character> flags = null;

    public CommandArgs(String cmd) {
      input = cmd;
      args = new ArrayList();
      flags = new HashSet();
    }

    private void parseArg(String arg) {
      if (arg.charAt(0) == '-') {                   // handle flags
        for (int i = 0; i < arg.length(); i++) {
          Character c = arg.charAt(i);
          flags.add(c);
        }
      } else {                                      // handle other args
        args.add(arg);
      }
    }

    public void parseArgs() {
      if (input == null)
        return;
      StringTokenizer st = new StringTokenizer(input);
      if (st.hasMoreTokens()) {
        command = st.nextToken();
        while (st.hasMoreTokens())
          parseArg(st.nextToken());
      }
    }
  }

  // Functions that each file system implementation must override

  public abstract String listAll(String path) throws InterpreterException;

  public abstract boolean isDirectory(String path);

  // Combine paths, takes care of arguments such as ..

  protected String getNewPath(String argument){
    Path arg = Paths.get(argument);
    Path ret = arg.isAbsolute() ? arg : Paths.get(currentDir, argument);
    return ret.normalize().toString();
  }

  // Handle the command handling uniformly across all file systems

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    logger.info("Run File command '" + cmd + "'");

    args = new CommandArgs(cmd);
    args.parseArgs();

    if (args.command == null) {
      logger.info("Error: No command");
      return new InterpreterResult(Code.ERROR, Type.TEXT, "No command");
    }

    // Simple parsing of the command

    if (args.command.equals("cd")) {

      String newPath = !args.args.isEmpty() ? getNewPath(args.args.get(0)) : currentDir;
      if (!isDirectory(newPath))
        return new InterpreterResult(Code.ERROR, Type.TEXT, newPath + ": No such directory");

      currentDir = newPath;
      return new InterpreterResult(Code.SUCCESS, Type.TEXT, "OK");

    } else if (args.command.equals("ls")) {

      String newPath = !args.args.isEmpty() ? getNewPath(args.args.get(0)) : currentDir;
      try {
        String results = listAll(newPath);
        return new InterpreterResult(Code.SUCCESS, Type.TEXT, results);
      } catch (Exception e) {
        logger.error("Error listing files in path " + newPath, e);
        return new InterpreterResult(Code.ERROR, Type.TEXT, e.getMessage());
      }

    } else if (args.command.equals("pwd")) {

      return new InterpreterResult(Code.SUCCESS, Type.TEXT, currentDir);

    } else {

      return new InterpreterResult(Code.ERROR, Type.TEXT, "Unknown command");

    }
  }

  @Override
  public void cancel(InterpreterContext context) {
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        FileInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    return null;
  }
}
