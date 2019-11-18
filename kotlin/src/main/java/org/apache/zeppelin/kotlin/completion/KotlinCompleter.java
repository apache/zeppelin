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

import static org.apache.zeppelin.kotlin.reflect.KotlinReflectUtil.shorten;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.kotlin.reflect.KotlinFunctionInfo;
import org.apache.zeppelin.kotlin.reflect.KotlinVariableInfo;
import org.apache.zeppelin.kotlin.repl.KotlinRepl;

public class KotlinCompleter {
  private static final List<InterpreterCompletion> keywords = KotlinKeywords.KEYWORDS.stream()
      .map(keyword -> new InterpreterCompletion(keyword, keyword, null))
      .collect(Collectors.toList());

  private KotlinRepl.KotlinContext ctx;

  public void setCtx(KotlinRepl.KotlinContext ctx) {
    this.ctx = ctx;
  }

  public List<InterpreterCompletion> completion(String buf, int cursor,
                                                InterpreterContext interpreterContext)  {
    if (ctx == null) {
      return new ArrayList<>(keywords);
    }

    List<InterpreterCompletion> result = new ArrayList<>();

    for (KotlinVariableInfo var : ctx.getVars()) {
      result.add(new InterpreterCompletion(
          var.getName(),
          var.getName(),
          shorten(var.getType())
      ));
    }

    for (KotlinFunctionInfo fun : ctx.getFunctions()) {
      result.add(new InterpreterCompletion(
          fun.getName(),
          fun.getName(),
          fun.toString(true)
      ));
    }

    result.addAll(keywords);
    return result;
  }
}
