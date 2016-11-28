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

import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;

public class MockInterpreterB extends Interpreter {
  static {
    Interpreter.register(
        "interpreterB",
        "group1",
        MockInterpreterA.class.getName(),
        new InterpreterPropertyBuilder()
            .add("p1", "v1", "property1").build());

  }
  public MockInterpreterB(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    //new RuntimeException().printStackTrace();
  }

  @Override
  public void close() {
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    MockInterpreterA intpA = getInterpreterA();
    String intpASt = intpA.getLastStatement();
    long timeToSleep = Long.parseLong(st);
    if (intpASt != null) {
      timeToSleep += Long.parseLong(intpASt);
    }
    try {
      Thread.sleep(timeToSleep);
    } catch (NumberFormatException | InterruptedException e) {
      throw new InterpreterException(e);
    }
    return new InterpreterResult(Code.SUCCESS, Long.toString(timeToSleep));
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
    return null;
  }

  public MockInterpreterA getInterpreterA() {
    InterpreterGroup interpreterGroup = getInterpreterGroup();
    synchronized (interpreterGroup) {
      for (List<Interpreter> interpreters : interpreterGroup.values()) {
        boolean belongsToSameNoteGroup = false;
        MockInterpreterA a = null;
        for (Interpreter intp : interpreters) {
          if (intp.getClassName().equals(MockInterpreterA.class.getName())) {
            Interpreter p = intp;
            while (p instanceof WrappedInterpreter) {
              p = ((WrappedInterpreter) p).getInnerInterpreter();
            }
            a = (MockInterpreterA) p;
          }

          Interpreter p = intp;
          while (p instanceof WrappedInterpreter) {
            p = ((WrappedInterpreter) p).getInnerInterpreter();
          }
          if (this == p) {
            belongsToSameNoteGroup = true;
          }
        }
        if (belongsToSameNoteGroup) {
          return a;
        }
      }
    }
    return null;
  }

  @Override
  public Scheduler getScheduler() {
    MockInterpreterA intpA = getInterpreterA();
    if (intpA != null) {
      return intpA.getScheduler();
    }
    return null;
  }

}
