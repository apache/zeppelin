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

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

/** MockInterpreter to test outputstream */
public class MockInterpreterOutputStream extends Interpreter {
  private String lastSt;

  public MockInterpreterOutputStream(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    // new RuntimeException().printStackTrace();
  }

  @Override
  public void close() {}

  public String getLastStatement() {
    return lastSt;
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    String[] ret = st.split(":");
    try {
      if (ret[1] != null) {
        context.out.write(ret[1]);
      }
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    return new InterpreterResult(
        InterpreterResult.Code.valueOf(ret[0]), (ret.length > 2) ? ret[2] : "");
  }

  @Override
  public void cancel(InterpreterContext context) {}

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(
      String buf, int cursor, InterpreterContext interpreterContext) {
    return null;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler("interpreter_" + this.hashCode());
  }
}
