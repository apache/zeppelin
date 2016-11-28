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
package org.apache.zeppelin.interpreter.dev;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;

/**
 * Dummy interpreter to support development mode for Zeppelin app
 */
public class DevInterpreter extends Interpreter {
  static {
    Interpreter.register(
        "dev",
        "dev",
        DevInterpreter.class.getName(),
        new InterpreterPropertyBuilder().build());
  }

  private InterpreterEvent interpreterEvent;
  private InterpreterContext context;

  public static boolean isInterpreterName(String replName) {
    return replName.equals("dev");
  }

  /**
   * event handler for ZeppelinApplicationDevServer
   */
  public static interface InterpreterEvent {
    public InterpreterResult interpret(String st, InterpreterContext context);
  }

  public DevInterpreter(Properties property) {
    super(property);
  }

  public DevInterpreter(Properties property, InterpreterEvent interpreterEvent) {
    super(property);
    this.interpreterEvent = interpreterEvent;
  }

  @Override
  public void open() {
  }

  @Override
  public void close() {
  }

  public void rerun() {
    for (InterpreterContextRunner r : context.getRunners()) {
      if (context.getParagraphId().equals(r.getParagraphId())) {
        r.run();
      }
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    this.context = context;
    try {
      return interpreterEvent.interpret(st, context);
    } catch (Exception e) {
      throw new InterpreterException(e);
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
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
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return new LinkedList<>();
  }

  public InterpreterContext getLastInterpretContext() {
    return context;
  }

  public void setInterpreterEvent(InterpreterEvent event) {
    this.interpreterEvent = event;
  }

  public InterpreterEvent getInterpreterEvent() {
    return interpreterEvent;
  }
}
