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

package org.apache.zeppelin.kotlin.reflect;

import org.jetbrains.kotlin.cli.common.repl.AggregatedReplStageState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kotlin.Pair;

public class KotlinStateUtil {
  private static Logger logger = LoggerFactory.getLogger(KotlinStateUtil.class);

  public static List<KotlinVariableInfo> runtimeVariables(AggregatedReplStageState<?, ?> state) {
    Map<String, KotlinVariableInfo> vars = new HashMap<>();
    Object script;
    try {
      Object statePair = Objects.requireNonNull(
          state.getHistory().peek())
          .getItem()
          .getSecond();
      script = ((Pair<?, ?>) statePair).getSecond();
    } catch (NullPointerException e) {
      return new ArrayList<>();
    }

    try {
      getVariablesFromScript(script, vars);
    } catch (ReflectiveOperationException e) {
      e.printStackTrace();
    }
    return new ArrayList<>(vars.values());
  }

  private static Object getImplicitReceiver(Object script)
      throws ReflectiveOperationException {
    Field receiverField = script.getClass().getDeclaredField("$$implicitReceiver0");
    return receiverField.get(script);
  }

  private static void getVariablesFromScript(Object script, Map<String, KotlinVariableInfo> vars)
      throws ReflectiveOperationException {
    ArrayDeque<Object> valuesToVisit = new ArrayDeque<>();
    valuesToVisit.add(script);
    valuesToVisit.add(getImplicitReceiver(script));

    while (!valuesToVisit.isEmpty()) {
      Object o = valuesToVisit.poll();
      Field[] fields = o.getClass().getDeclaredFields();

      for (Field field : fields) {
        String fieldName = field.getName();

        if (vars.containsKey(fieldName)
            || fieldName.contains("$$implicitReceiver")
            || fieldName.contains("kotlinVars")) {
          continue;
        }
        field.setAccessible(true);

        Object value = field.get(o);
        if (fieldName.contains("script$")) {
          valuesToVisit.add(value);
        } else {
          vars.put(fieldName, new KotlinVariableInfo(fieldName, value, field));
        }
      }
    }
  }
}
