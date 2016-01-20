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

package org.apache.zeppelin.markdown;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.markdown4j.Markdown4jProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Markdown interpreter for Zeppelin.
 */
public class Markdown extends Interpreter {
  private Markdown4jProcessor md;
  static final Logger LOGGER = LoggerFactory.getLogger(Markdown.class);

  static {
    Interpreter.register("md", Markdown.class.getName());
  }

  public Markdown(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    md = new Markdown4jProcessor();
  }

  @Override
  public void close() {}

  @Override
  public InterpreterResult interpret(String st, InterpreterContext interpreterContext) {
    String html;
    try {
      html = md.process(st);
    } catch (IOException | java.lang.RuntimeException e) {
      LOGGER.error("Exception in Markdown while interpret ", e);
      return new InterpreterResult(Code.ERROR, InterpreterUtils.getMostRelevantMessage(e));
    }
    return new InterpreterResult(Code.SUCCESS, "%html " + html);
  }

  @Override
  public void cancel(InterpreterContext context) {}

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
        Markdown.class.getName() + this.hashCode(), 5);
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }
}
