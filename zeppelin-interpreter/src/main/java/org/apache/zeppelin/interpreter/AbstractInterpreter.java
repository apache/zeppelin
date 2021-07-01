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

package org.apache.zeppelin.interpreter;

import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractInterpreter extends Interpreter {

  private static final Pattern VARIABLES = Pattern.compile("([^{}]*)([{]+[^{}]*[}]+)(.*)", Pattern.DOTALL);
  private static final Pattern VARIABLE_IN_BRACES = Pattern.compile("[{][^{}]+[}]");
  private static final Pattern VARIABLE_IN_DOUBLE_BRACES = Pattern.compile("[{]{2}[^{}]+[}]{2}");

  public AbstractInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public InterpreterResult interpret(String st,
                                     InterpreterContext context) throws InterpreterException {
    InterpreterContext.set(context);
    ZeppelinContext z = getZeppelinContext();
    if (z != null) {
      z.setGui(context.getGui());
      z.setNoteGui(context.getNoteGui());
      z.setInterpreterContext(context);
    }
    boolean interpolate = isInterpolate() ||
            Boolean.parseBoolean(context.getLocalProperties().getOrDefault("interpolate", "false"));
    if (interpolate) {
      st = interpolate(st, context.getResourcePool());
    }
    return internalInterpret(st, context);
  }

  static String interpolate(String cmd, ResourcePool resourcePool) {

    StringBuilder sb = new StringBuilder();
    Matcher m;
    String st = cmd;
    while ((m = VARIABLES.matcher(st)).matches()) {
      sb.append(m.group(1));
      String varPat = m.group(2);
      if (VARIABLE_IN_BRACES.matcher(varPat).matches()) {
        // substitute {variable} only if 'variable' has a value ...
        Resource resource = resourcePool.get(varPat.substring(1, varPat.length() - 1));
        Object variableValue = resource == null ? null : resource.get();
        if (variableValue != null)
          sb.append(variableValue);
        else
          return cmd;
      } else if (VARIABLE_IN_DOUBLE_BRACES.matcher(varPat).matches()) {
        // escape {{text}} ...
        sb.append("{").append(varPat, 2, varPat.length() - 2).append("}");
      } else {
        // mismatched {{ }} or more than 2 braces ...
        return cmd;
      }
      st = m.group(3);
    }
    sb.append(st);
    return sb.toString();
  }

  public abstract ZeppelinContext getZeppelinContext();

  protected boolean isInterpolate() {
    return false;
  }

  protected abstract InterpreterResult internalInterpret(
          String st,
          InterpreterContext context) throws InterpreterException;

  @Override
  public List<InterpreterCompletion> completion(String buf,
                                                int cursor,
                                                InterpreterContext interpreterContext) throws InterpreterException {
    return new ArrayList<>();
  }
}
