/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.python;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * SQL over Pandas DataFrame interpreter for %python group
 * <p>
 * Match experience of %spark.sql over Spark DataFrame
 */
public class PythonInterpreterPandasSql extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(PythonInterpreterPandasSql.class);
  private static String SQL_BOOTSTRAP_FILE_PY = "python/bootstrap_sql.py";

  private PythonInterpreter pythonInterpreter;

  public PythonInterpreterPandasSql(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    LOGGER.info("Open Python SQL interpreter instance: PythonInterpreterPandasSql");
    try {
      LOGGER.info("Bootstrap PythonInterpreterPandasSql interpreter with {}",
              SQL_BOOTSTRAP_FILE_PY);
      this.pythonInterpreter = getInterpreterInTheSameSessionByClassName(PythonInterpreter.class);
      this.pythonInterpreter.bootstrapInterpreter(SQL_BOOTSTRAP_FILE_PY);
    } catch (IOException e) {
      LOGGER.error("Can't execute " + SQL_BOOTSTRAP_FILE_PY + " to import SQL dependencies", e);
    }
  }

  @Override
  public void close() throws InterpreterException {
    LOGGER.info("Close Python SQL interpreter instance: {}", this.toString());
    if (pythonInterpreter != null) {
      pythonInterpreter.close();
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    LOGGER.info("Running SQL query: '{}' over Pandas DataFrame", st);
    return pythonInterpreter.interpret(
        "z.show(pysqldf('" + st + "'))", context);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    pythonInterpreter.cancel(context);
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return pythonInterpreter.getProgress(context);
  }

}
