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
import org.jetbrains.kotlin.cli.common.repl.ReplHistoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kotlin.Pair;
import kotlin.reflect.KFunction;
import kotlin.reflect.KProperty;
import kotlin.reflect.jvm.ReflectJvmMapping;

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
      Set<KFunction<?>> methods,
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

  public static String functionSignature(KFunction<?> function) {
    return function.toString().replaceAll("Line_\\d+\\.", "");
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
      findReceiverVariables(vars, receiver);
    }
    for (Object line : lines) {
      findLineVariables(vars, line);
    }
  }

  // For lines, we only want fields from top level class
  private static void findLineVariables(Map<String, KotlinVariableInfo> vars, Object line)
      throws IllegalAccessException {
    Field[] fields = line.getClass().getDeclaredFields();
    findVariables(vars, fields, line);
  }

  // For implicit receiver, we want to also get fields in parent classes
  private static void findReceiverVariables(Map<String, KotlinVariableInfo> vars, Object receiver)
      throws IllegalAccessException {
    List<Field> fieldsList = new ArrayList<>();
    for (Class<?> cl = receiver.getClass(); cl != null; cl = cl.getSuperclass()) {
      fieldsList.addAll(Arrays.asList(cl.getDeclaredFields()));
    }
    findVariables(vars, fieldsList.toArray(new Field[0]), receiver);
  }

  private static void findVariables(Map<String, KotlinVariableInfo> vars, Field[] fields, Object o)
      throws IllegalAccessException {
    for (Field field : fields) {
      String fieldName = field.getName();
      if (fieldName.contains("$$implicitReceiver")) {
        continue;
      }

      field.setAccessible(true);
      Object value = field.get(o);
      if (!fieldName.contains("script$")) {
        KProperty<?> descriptor = ReflectJvmMapping.getKotlinProperty(field);
        if (descriptor != null) {
          vars.putIfAbsent(fieldName, new KotlinVariableInfo(value, descriptor));
        }
      }
    }
  }

  private static void getNewMethods(
      Object script,
      Set<KFunction<?>> methods) {
    Set<KFunction<?>> newMethods = new HashSet<>();
    Method[] scriptMethods = script.getClass().getMethods();
    for (Method method : scriptMethods) {
      if (objectMethods.contains(method) || method.getName().equals("main")) {
        continue;
      }
      KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
      if (function != null) {
        newMethods.add(function);
      }
    }
    methods.addAll(newMethods);
  }

  private static Object getImplicitReceiver(Object script)
      throws ReflectiveOperationException {
    Field receiverField = script.getClass().getDeclaredField("$$implicitReceiver0");
    return receiverField.get(script);
  }
}
