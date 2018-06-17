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
package org.apache.zeppelin.ksql;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static java.lang.Integer.parseInt;

/**
 * Interpreter for KSQL.
 */
public class KsqlInterpreter extends Interpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(KsqlInterpreter.class);

  public static final String KSQL_INTERPRETER_PARALLELISM =
          "ksql.interpreter.parallelism";
  public static final String KSQL_URL = "ksql.url";

  public static final String DEFAULT_URL = "http://localhost:8088";

  public KsqlInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() {
    final String url = getProperty(KSQL_URL, DEFAULT_URL);

  }

  @Override
  public void close() {
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    return null;
  }

  @Override
  public void cancel(InterpreterContext context) {
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    return Collections.emptyList();
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton()
            .createOrGetParallelScheduler(KsqlInterpreter.class.getName() + this.hashCode(),
                    parseInt(getProperty(KSQL_INTERPRETER_PARALLELISM)));
  }
}
