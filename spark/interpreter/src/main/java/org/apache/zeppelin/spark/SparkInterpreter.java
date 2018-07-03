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
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

/**
 * It is the Wrapper of OldSparkInterpreter & NewSparkInterpreter.
 * Property zeppelin.spark.useNew control which one to use.
 */
public class SparkInterpreter extends AbstractSparkInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkInterpreter.class);

  // either OldSparkInterpreter or NewSparkInterpreter
  private AbstractSparkInterpreter delegation;


  public SparkInterpreter(Properties properties) {
    super(properties);
    if (Boolean.parseBoolean(properties.getProperty("zeppelin.spark.useNew", "false"))) {
      delegation = new NewSparkInterpreter(properties);
    } else {
      delegation = new OldSparkInterpreter(properties);
    }
    delegation.setParentSparkInterpreter(this);
  }

  @Override
  public void open() throws InterpreterException {
    delegation.setInterpreterGroup(getInterpreterGroup());
    delegation.setUserName(getUserName());
    delegation.setClassloaderUrls(getClassloaderUrls());

    delegation.open();
  }

  @Override
  public void close() throws InterpreterException {
    delegation.close();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    return delegation.interpret(st, context);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    delegation.cancel(context);
  }

  @Override
  public List<InterpreterCompletion> completion(String buf,
                                                int cursor,
                                                InterpreterContext interpreterContext)
      throws InterpreterException {
    return delegation.completion(buf, cursor, interpreterContext);
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return delegation.getProgress(context);
  }

  public AbstractSparkInterpreter getDelegation() {
    return delegation;
  }


  @Override
  public SparkContext getSparkContext() {
    return delegation.getSparkContext();
  }

  @Override
  public SQLContext getSQLContext() {
    return delegation.getSQLContext();
  }

  @Override
  public Object getSparkSession() {
    return delegation.getSparkSession();
  }

  @Override
  public boolean isSparkContextInitialized() {
    return delegation.isSparkContextInitialized();
  }

  @Override
  public SparkVersion getSparkVersion() {
    return delegation.getSparkVersion();
  }

  @Override
  public JavaSparkContext getJavaSparkContext() {
    return delegation.getJavaSparkContext();
  }

  @Override
  public SparkZeppelinContext getZeppelinContext() {
    return delegation.getZeppelinContext();
  }

  @Override
  public String getSparkUIUrl() {
    return delegation.getSparkUIUrl();
  }

  public boolean isUnsupportedSparkVersion() {
    return delegation.isUnsupportedSparkVersion();
  }

  public boolean isYarnMode() {
    String master = getProperty("master");
    if (master == null) {
      master = getProperty("spark.master", "local[*]");
    }
    return master.startsWith("yarn");
  }

  public static boolean useSparkSubmit() {
    return null != System.getenv("SPARK_SUBMIT");
  }
}
