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

import java.util.Properties;

public abstract class AbstractInterpreter extends Interpreter {

  public AbstractInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public InterpreterResult interpret(String st,
                                     InterpreterContext context) throws InterpreterException {
    InterpreterContext.set(context);
    BaseZeppelinContext z = getZeppelinContext();
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

  public abstract BaseZeppelinContext getZeppelinContext();

  protected boolean isInterpolate() {
    return false;
  }

  protected abstract InterpreterResult internalInterpret(
          String st,
          InterpreterContext context) throws InterpreterException;
}
