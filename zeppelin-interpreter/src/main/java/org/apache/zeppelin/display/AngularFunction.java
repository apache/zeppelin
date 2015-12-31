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
package org.apache.zeppelin.display;

import com.google.gson.Gson;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * AngularFunction provides proxy object in front-end side.
 * Calling front-end side proxy will invoke AngularFunctionRunnable of this AngularFunction
 */
public class AngularFunction extends AngularObjectWatcher {
  Logger logger = LoggerFactory.getLogger(AngularFunction.class);
  private static final String ANGULAR_FUNCTION_OBJECT_NAME_PREFIX = "_Z_ANGULAR_FUNC_";
  private final AngularObjectRegistry registry;
  private final String name;
  private final String noteId;
  private final AngularFunctionRunnable runnable;

  // each invocation of from front-end proxy function will increase the counter
  AngularObject counter;
  // arguments of invocation from fron-end proxy function
  AngularObject args;
  // return value from AngularFunction to front-end proxy function
  AngularObject ret;

  protected AngularFunction(AngularObjectRegistry registry,
                         String name, String noteId,
                         AngularFunctionRunnable runnable) {
    super(null);
    this.name = name;
    this.noteId = noteId;
    this.runnable = runnable;
    this.registry = registry;

    remove();

    ret = registry.add(getRetName(name), null, noteId);
    args = registry.add(getArgsName(name), null, noteId);
    counter = registry.add(getCounterName(name), 0, noteId);
    counter.addWatcher(this);
  }


  static String getCounterName(String name) {
    return ANGULAR_FUNCTION_OBJECT_NAME_PREFIX + "COUNTER_" + name;
  }

  static String getArgsName(String name) {
    return ANGULAR_FUNCTION_OBJECT_NAME_PREFIX + "ARGS_" + name;
  }

  static String getRetName(String name) {
    return ANGULAR_FUNCTION_OBJECT_NAME_PREFIX + "RET_" + name;
  }

  @Override
  public void watch(Object oldObject, Object newObject, InterpreterContext context) {
    if (runnable == null) {
      return;
    }

    Object argumentList = args.get();
    Object returnValue;
    if (argumentList instanceof Object[]) {
      returnValue = runnable.run((Object[]) args.get());
    } else {
      returnValue = runnable.run(args.get());
    }

    ret.set(returnValue);
  }


  void remove() {
    registry.remove(getCounterName(name), noteId);
    registry.remove(getArgsName(name), noteId);
    registry.remove(getRetName(name), noteId);
  }

  public String getNoteId() {
    return noteId;
  }
}
