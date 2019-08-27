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

import static kotlin.jvm.internal.Reflection.typeOf;
import org.jetbrains.kotlin.cli.common.repl.AggregatedReplStageState;
import org.jetbrains.kotlin.cli.common.repl.ReplHistoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import kotlin.Pair;

public class KotlinReflectUtil {
  private static Logger logger = LoggerFactory.getLogger(KotlinReflectUtil.class);
  private static final Set<Method> objectMethods =
      new HashSet<>(Arrays.asList(Object.class.getMethods()));

  public static void updateVars(
      Map<String, KotlinVariableInfo> vars,
      AggregatedReplStageState<?, ?> state) {
    try {
      List<Object> lines = getLines(state);
      refreshVariables(lines, vars);
    } catch (ReflectiveOperationException | NullPointerException e) {
      logger.error("Exception updating current variables", e);
    }
  }

  public static void updateMethods(
      Set<Method> methods,
      AggregatedReplStageState<?, ?> state) {
    try {
      if (state.getHistory().isEmpty()) {
        return;
      }
      Object script = getLineFromRecord(state.getHistory().peek());
      getNewMethods(script, methods);
    } catch (NullPointerException e) {
      logger.error("Exception updating current methods", e);
    }
  }

  public static String kotlinTypeName(Object o) {
    Class oc = o.getClass();
    if (oc.getGenericSuperclass() instanceof ParameterizedType) {
      return oc.getSimpleName();
    }

    String kotlinName = typeOf(oc).toString();
    if (kotlinName.startsWith("kotlin.")) {
      String[] tokens = kotlinName.split("\\.");
      return tokens[tokens.length - 1];
    }
    return kotlinName;
  }

  private static String kotlinTypeName(Class<?> c) {
    return typeOf(c).toString();
  }

  public static String kotlinMethodSignature(Method method) {
    StringJoiner joiner = new StringJoiner(", ");
    for (Class<?> param : method.getParameterTypes()) {
      joiner.add(kotlinTypeName(param));
    }
    return method.getName() +
        "(" + joiner.toString() + "): " +
        kotlinTypeName(method.getReturnType());
  }

  private static List<Object> getLines(AggregatedReplStageState<?, ?> state) {
    List<Object> lines = state.getHistory().stream()
        .map(KotlinReflectUtil::getLineFromRecord)
        .collect(Collectors.toList());
    Collections.reverse(lines);
    return lines;
  }

  private static Object getLineFromRecord(ReplHistoryRecord<? extends Pair<?, ?>> record) {
    Object statePair = record.getItem().getSecond();
    return ((Pair<?, ?>) statePair).getSecond();
  }

  private static void refreshVariables(
      List<Object> lines,
      Map<String, KotlinVariableInfo> vars) throws ReflectiveOperationException {

    vars.clear();
    if (!lines.isEmpty()) {
      Object receiver = getImplicitReceiver(lines.get(0));
      findVariables(vars, receiver);
    }
    for (Object line : lines) {
      findVariables(vars, line);
    }
  }

  private static void findVariables(Map<String, KotlinVariableInfo> vars, Object line)
      throws IllegalAccessException {
    Field[] fields = line.getClass().getDeclaredFields();
    for (Field field : fields) {
      String fieldName = field.getName();
      if (fieldName.contains("$$implicitReceiver")) {
        continue;
      }

      field.setAccessible(true);
      Object value = field.get(line);
      if (!fieldName.contains("script$")) {
        vars.putIfAbsent(fieldName, new KotlinVariableInfo(fieldName, value, field));
      }
    }
  }

  private static void getNewMethods(
      Object script,
      Set<Method> methods) {
    Set<Method> newMethods = new HashSet<>(Arrays.asList(
        script.getClass().getMethods()));
    newMethods.removeAll(objectMethods);
    newMethods.removeIf(method -> method.getName().equals("main"));
    methods.addAll(newMethods);
  }


  private static Object getImplicitReceiver(Object script)
      throws ReflectiveOperationException {
    Field receiverField = script.getClass().getDeclaredField("$$implicitReceiver0");
    return receiverField.get(script);
  }
}
