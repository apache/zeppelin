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

package org.apache.zeppelin.js;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Javascript interpreter for Zeppelin.
 *
 * @author Leonardo Foderaro
 *
 *
 */
public class JsInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(JsInterpreter.class);
  int commandTimeOut = 600000;

  ScriptEngine engine;

  static {
    Interpreter.register("js", JsInterpreter.class.getName());
  }

  public JsInterpreter(Properties property) {
    super(property);

    engine = new ScriptEngineManager().getEngineByName("nashorn");

  }

  @Override
  public void open() {
  }

  @Override
  public void close() {
  }

  @Override
  public InterpreterResult interpret(String cmd,
      InterpreterContext contextInterpreter) {
    logger.info("Run js command '" + cmd + "'");
    long start = System.currentTimeMillis();

    StringWriter sw = new StringWriter();

    String result;

    engine.getContext().setWriter(sw);

    try {
      Object o = engine.eval(new StringReader(cmd));

      if (o != null) {
        result = sw.toString() + "\n\r" + o.toString();
      } else {
        result = sw.toString();
      }

      return new InterpreterResult(InterpreterResult.Code.SUCCESS, result.toString());
    } catch (ScriptException e) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
    }

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
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
               JsInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}
