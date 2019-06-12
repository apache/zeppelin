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

package org.apache.zeppelin.spark;

import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;

import java.util.List;

/**
 * This is bridge class which bridge the communication between java side and scala side.
 * Java side reply on this abstract class which is implemented by different scala versions.
 */
public abstract class AbstractSparkScalaInterpreter {

  public abstract SparkContext getSparkContext();

  public abstract SQLContext getSqlContext();

  public abstract Object getSparkSession();

  public abstract String getSparkUrl();

  public abstract BaseZeppelinContext getZeppelinContext();

  public int getProgress(InterpreterContext context) throws InterpreterException {
    return getProgress(Utils.buildJobGroupId(context), context);
  }

  public abstract int getProgress(String jobGroup,
                                  InterpreterContext context) throws InterpreterException;

  public void cancel(InterpreterContext context) throws InterpreterException {
    getSparkContext().cancelJobGroup(Utils.buildJobGroupId(context));
  }

  public Interpreter.FormType getFormType() throws InterpreterException {
    return Interpreter.FormType.SIMPLE;
  }

  public abstract void open();

  public abstract void close();

  public abstract InterpreterResult interpret(String st, InterpreterContext context);

  public abstract List<InterpreterCompletion> completion(String buf,
                                                         int cursor,
                                                         InterpreterContext interpreterContext);
}
