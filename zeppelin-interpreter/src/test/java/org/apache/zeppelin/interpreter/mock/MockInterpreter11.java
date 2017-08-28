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

package org.apache.zeppelin.interpreter.mock;


import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MockInterpreter11 extends Interpreter {
  Map<String, Object> vars = new HashMap<>();

  public MockInterpreter11(Properties property) {
    super(property);
  }

  boolean open;

  @Override
  public void open() {
    open = true;
  }

  @Override
  public void close() {
    open = false;
  }

  public boolean isOpen() {
    return open;
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    return new InterpreterResult(InterpreterResult.Code.SUCCESS, "repl11: " + st);
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
    return SchedulerFactory.singleton().createOrGetFIFOScheduler("test_" + this.hashCode());
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
                                                InterpreterContext interpreterContext) {
    return null;
  }
}