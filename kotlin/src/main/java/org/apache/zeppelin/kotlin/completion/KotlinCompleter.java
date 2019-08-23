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

package org.apache.zeppelin.kotlin.completion;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.kotlin.KotlinRepl;
import org.apache.zeppelin.kotlin.reflect.KotlinVariableInfo;

public class KotlinCompleter {
  private KotlinRepl.KotlinContext ctx;

  public KotlinCompleter(KotlinRepl.KotlinContext ctx) {
    this.ctx = ctx;
  }

  public List<InterpreterCompletion> completion(String buf, int cursor,
                                                InterpreterContext interpreterContext)  {
    List<InterpreterCompletion> result = new ArrayList<>();
    for (KotlinVariableInfo var : ctx.getVars()) {
      result.add(new InterpreterCompletion(
          var.getName(),
          var.getName(),
          var.getDescriptor().getType().getSimpleName()
      ));
    }

    List<Method> methods = ctx.getMethods();
    for (Method method : methods) {
      result.add(new InterpreterCompletion(
          method.getName(),
          method.getName(),
          methodSignature(method)
      ));
    }
    return result;
  }

  private String methodSignature(Method method) {
    StringJoiner joiner = new StringJoiner(", ");
    for (Class<?> param : method.getParameterTypes()) {
      joiner.add(param.getSimpleName());
    }
    return method.getName() +
        "(" + joiner.toString() + "): " +
        method.getReturnType().getSimpleName();
  }
  /*
  private List<InterpreterCompletion> getFieldsAndMethods(KotlinVariableInfo var) {

    Class<?> varClass = var.getDescriptor().getType();
    List<InterpreterCompletion> result = new ArrayList<>();

    for (Field field : varClass.getFields()) {
      String name = field.getName();
      String meta = field.getType().getSimpleName();
      result.add(new InterpreterCompletion(name, name, meta));
    }
    for (Method method : varClass.getMethods()) {
      String name = method.getName();
      String meta = getMethodSignature(method);
      result.add(new InterpreterCompletion(name, name, meta));
    }

    return result;
  }

  */
}
