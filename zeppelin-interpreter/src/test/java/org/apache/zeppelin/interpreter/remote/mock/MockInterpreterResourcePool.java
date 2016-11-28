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

import com.google.gson.Gson;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectWatcher;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;

public class MockInterpreterResourcePool extends Interpreter {
  static {
    Interpreter.register(
        "resourcePoolTest",
        "resourcePool",
        MockInterpreterA.class.getName(),
        new InterpreterPropertyBuilder()
            .add("p1", "v1", "property1").build());

  }

  AtomicInteger numWatch = new AtomicInteger(0);

  public MockInterpreterResourcePool(Properties property) {
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
    String noteId = null;
    String paragraphId = null;
    String name = null;
    if (stmt.length >= 2) {
      String[] npn = stmt[1].split(":");
      if (npn.length == 3) {
        noteId = npn[0];
        paragraphId = npn[1];
        name = npn[2];
      } else {
        name = stmt[1];
      }
    }
    String value = null;
    if (stmt.length == 3) {
      value = stmt[2];
    }

    ResourcePool resourcePool = context.getResourcePool();
    Object ret = null;
    if (cmd.equals("put")) {
      resourcePool.put(noteId, paragraphId, name, value);
    } else if (cmd.equalsIgnoreCase("get")) {
      Resource resource = resourcePool.get(noteId, paragraphId, name);
      if (resource != null) {
        ret = resourcePool.get(noteId, paragraphId, name).get();
      } else {
        ret = "";
      }
    } else if (cmd.equals("remove")) {
      ret = resourcePool.remove(noteId, paragraphId, name);
    } else if (cmd.equals("getAll")) {
      ret = resourcePool.getAll();
    }

    try {
      Thread.sleep(500); // wait for watcher executed
    } catch (InterruptedException e) {
    }

    Gson gson = new Gson();
    return new InterpreterResult(Code.SUCCESS, gson.toJson(ret));
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
}