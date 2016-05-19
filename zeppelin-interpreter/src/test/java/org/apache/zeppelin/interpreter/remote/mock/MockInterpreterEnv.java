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
package org.apache.zeppelin.interpreter.remote.mock;

import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import java.util.List;
import java.util.Properties;


public class MockInterpreterEnv extends Interpreter {
  static {
    Interpreter.register(
        "interpreterA",
        "group1",
        MockInterpreterA.class.getName(),
        new InterpreterPropertyBuilder().build());

  }


  public MockInterpreterEnv(Properties property) {
    super(property);
  }

  @Override
  public void open() {
  }

  @Override
  public void close() {
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    String[] cmd = st.split(" ");
    if (cmd[0].equals("getEnv")) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, System.getenv(cmd[1]));
    } else if (cmd[0].equals("getProperty")){
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, System.getProperty(cmd[1]));
    } else {
      return new InterpreterResult(InterpreterResult.Code.ERROR, cmd[0]);
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
  public List<String> completion(String buf, int cursor) {
    return null;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler("interpreter_" + this.hashCode());
  }
}

