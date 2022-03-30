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
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.r.RInterpreter;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * R and SparkR interpreter with visualization support.
 */
public class SparkRInterpreter extends RInterpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(SparkRInterpreter.class);

  private SparkInterpreter sparkInterpreter;
  private SparkVersion sparkVersion;
  private SparkContext sc;
  private JavaSparkContext jsc;

  public SparkRInterpreter(Properties property) {
    super(property);
  }

  @Override
  protected boolean isSparkSupported() {
    return true;
  }

  @Override
  protected boolean isSecretSupported() {
    return sparkVersion.isSecretSocketSupported();
  }

  @Override
  protected int sparkVersion() {
    return new SparkVersion(sc.version()).toNumber();
  }

  @Override
  public void open() throws InterpreterException {
    this.sparkInterpreter = getInterpreterInTheSameSessionByClassName(SparkInterpreter.class);
    this.sc = sparkInterpreter.getSparkContext();
    this.jsc = sparkInterpreter.getJavaSparkContext();
    this.sparkVersion = new SparkVersion(sc.version());

    LOGGER.info("SparkRInterpreter: SPARK_HOME={}", sc.getConf().getenv("SPARK_HOME"));
    Arrays.stream(sc.getConf().getAll())
            .forEach(x -> LOGGER.info("SparkRInterpreter: conf, {}={}", x._1, x._2));
    properties.entrySet().stream().forEach(x ->
            LOGGER.info("SparkRInterpreter: prop, {}={}", x.getKey(), x.getValue()));

    ZeppelinRContext.setSparkContext(sc);
    ZeppelinRContext.setJavaSparkContext(jsc);
    ZeppelinRContext.setSparkSession(sparkInterpreter.getSparkSession());
    ZeppelinRContext.setSqlContext(sparkInterpreter.getSQLContext());
    ZeppelinRContext.setZeppelinContext(sparkInterpreter.getZeppelinContext());
    super.open();
  }

  @Override
  public InterpreterResult internalInterpret(String lines, InterpreterContext interpreterContext)
      throws InterpreterException {
    Utils.printDeprecateMessage(sparkInterpreter.getSparkVersion(),
            interpreterContext, properties);
    String jobGroup = Utils.buildJobGroupId(interpreterContext);
    String jobDesc = Utils.buildJobDesc(interpreterContext);
    sparkInterpreter.getSparkContext().setJobGroup(jobGroup, jobDesc, false);
    String setJobGroup = "";
    // assign setJobGroup to dummy__, otherwise it would print NULL for this statement
    setJobGroup = "dummy__ <- setJobGroup(\"" + jobGroup +
        "\", \" +" + jobDesc + "\", TRUE)";
    lines = setJobGroup + "\n" + lines;
    if (sparkInterpreter.getSparkVersion().newerThanEquals(SparkVersion.SPARK_2_3_0)) {
      // setLocalProperty is only available from spark 2.3.0
      String setPoolStmt = "setLocalProperty('spark.scheduler.pool', NULL)";
      if (interpreterContext.getLocalProperties().containsKey("pool")) {
        setPoolStmt = "setLocalProperty('spark.scheduler.pool', '" +
            interpreterContext.getLocalProperties().get("pool") + "')";
      }
      lines = setPoolStmt + "\n" + lines;
    }
    return super.internalInterpret(lines, interpreterContext);
  }

  @Override
  public void close() throws InterpreterException {
    super.close();
  }

  @Override
  public void cancel(InterpreterContext context) {
    if (this.sc != null) {
      sc.cancelJobGroup(Utils.buildJobGroupId(context));
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    if (sparkInterpreter != null) {
      return sparkInterpreter.getProgress(context);
    } else {
      return 0;
    }
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
            SparkRInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return sparkInterpreter.getZeppelinContext();
  }

}
