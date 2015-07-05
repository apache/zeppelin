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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectWatcher;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;

public class MockInterpreterAngular extends Interpreter {
  static {
    Interpreter.register(
        "angularTest",
        "angular",
        MockInterpreterA.class.getName(),
        new InterpreterPropertyBuilder()
            .add("p1", "v1", "property1").build());

  }

  AtomicInteger numWatch = new AtomicInteger(0);

  public MockInterpreterAngular(Properties property) {
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
    String[] stmt = st.split(" ");
    String cmd = stmt[0];
    String name = null;
    if (stmt.length >= 2) {
      name = stmt[1];
    }
    String value = null;
    if (stmt.length == 3) {
      value = stmt[2];
    }

    AngularObjectRegistry registry = context.getAngularObjectRegistry();

    if (cmd.equals("add")) {
      registry.add(name, value, context.getNoteId());
      registry.get(name, context.getNoteId()).addWatcher(new AngularObjectWatcher(null) {

        @Override
        public void watch(Object oldObject, Object newObject,
            InterpreterContext context) {
          numWatch.incrementAndGet();
        }

      });
    } else if (cmd.equalsIgnoreCase("update")) {
      registry.get(name, context.getNoteId()).set(value);
    } else if (cmd.equals("remove")) {
      registry.remove(name, context.getNoteId());
    }

    try {
      Thread.sleep(500); // wait for watcher executed
    } catch (InterruptedException e) {
    }

    String msg = registry.getAll(context.getNoteId()).size() + " " + Integer.toString(numWatch.get());
    return new InterpreterResult(Code.SUCCESS, msg);
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
}
