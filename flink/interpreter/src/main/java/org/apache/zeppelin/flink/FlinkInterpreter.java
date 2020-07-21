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

package org.apache.zeppelin.flink;

import org.apache.flink.api.scala.ExecutionEnvironment;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.catalog.CatalogManager;
import org.apache.flink.table.delegation.Planner;
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
 * Interpreter for flink scala. It delegates all the function to FlinkScalaInterpreter.
 */
public class FlinkInterpreter extends Interpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlinkInterpreter.class);

  private FlinkScalaInterpreter innerIntp;
  private FlinkZeppelinContext z;

  public FlinkInterpreter(Properties properties) {
    super(properties);
  }

  private void checkScalaVersion() throws InterpreterException {
    String scalaVersionString = scala.util.Properties.versionString();
    LOGGER.info("Using Scala: " + scalaVersionString);
    if (scalaVersionString.contains("version 2.11")) {
      return;
    } else {
      throw new InterpreterException("Unsupported scala version: " + scalaVersionString +
              ", Only scala 2.11 is supported");
    }
  }

  @Override
  public void open() throws InterpreterException {
    checkScalaVersion();
    
    this.innerIntp = new FlinkScalaInterpreter(getProperties());
    this.innerIntp.open();
    this.z = this.innerIntp.getZeppelinContext();
  }

  @Override
  public void close() throws InterpreterException {
    if (this.innerIntp != null) {
      this.innerIntp.close();
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    LOGGER.debug("Interpret code: " + st);
    this.z.setInterpreterContext(context);
    this.z.setGui(context.getGui());
    this.z.setNoteGui(context.getNoteGui());

    // set ClassLoader of current Thread to be the ClassLoader of Flink scala-shell,
    // otherwise codegen will fail to find classes defined in scala-shell
    ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(getFlinkScalaShellLoader());
      createPlannerAgain();
      setParallelismIfNecessary(context);
      setSavePointIfNecessary(context);
      return innerIntp.interpret(st, context);
    } finally {
      Thread.currentThread().setContextClassLoader(originClassLoader);
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    this.innerIntp.cancel(context);
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return this.innerIntp.getProgress(context);
  }

  @Override
  public List<InterpreterCompletion> completion(String buf,
                                                int cursor,
                                                InterpreterContext interpreterContext)
      throws InterpreterException {
    return innerIntp.completion(buf, cursor, interpreterContext);
  }

  ExecutionEnvironment getExecutionEnvironment() {
    return this.innerIntp.getExecutionEnvironment();
  }

  StreamExecutionEnvironment getStreamExecutionEnvironment() {
    return this.innerIntp.getStreamExecutionEnvironment();
  }

  TableEnvironment getStreamTableEnvironment() {
    return this.innerIntp.getStreamTableEnvironment("blink");
  }

  org.apache.flink.table.api.TableEnvironment getJavaBatchTableEnvironment(String planner) {
    return this.innerIntp.getJavaBatchTableEnvironment(planner);
  }

  TableEnvironment getJavaStreamTableEnvironment(String planner) {
    return this.innerIntp.getJavaStreamTableEnvironment(planner);
  }

  TableEnvironment getBatchTableEnvironment() {
    return this.innerIntp.getBatchTableEnvironment("blink");
  }

  JobManager getJobManager() {
    return this.innerIntp.getJobManager();
  }

  int getDefaultParallelism() {
    return this.innerIntp.getDefaultParallelism();
  }

  int getDefaultSqlParallelism() {
    return this.innerIntp.getDefaultSqlParallelism();
  }

  /**
   * Workaround for issue of FLINK-16936.
   */
  public void createPlannerAgain() {
    this.innerIntp.createPlannerAgain();
  }

  public ClassLoader getFlinkScalaShellLoader() {
    return innerIntp.getFlinkScalaShellLoader();
  }

  FlinkZeppelinContext getZeppelinContext() {
    return this.z;
  }

  Configuration getFlinkConfiguration() {
    return this.innerIntp.getConfiguration();
  }

  public FlinkScalaInterpreter getInnerIntp() {
    return this.innerIntp;
  }

  public FlinkShims getFlinkShims() {
    return this.innerIntp.getFlinkShims();
  }

  public void setSavePointIfNecessary(InterpreterContext context) {
    this.innerIntp.setSavePointIfNecessary(context);
  }

  public void setParallelismIfNecessary(InterpreterContext context) {
    this.innerIntp.setParallelismIfNecessary(context);
  }

  public FlinkVersion getFlinkVersion() {
    return this.innerIntp.getFlinkVersion();
  }
}
