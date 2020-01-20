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
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.r.IRInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * SparkR Interpreter which uses irkernel underneath.
 */
public class SparkIRInterpreter extends IRInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkRInterpreter.class);

  private SparkInterpreter sparkInterpreter;
  private SparkVersion sparkVersion;
  private boolean isSpark2;
  private SparkContext sc;
  private JavaSparkContext jsc;

  public SparkIRInterpreter(Properties properties) {
    super(properties);
  }

  protected boolean isSparkSupported() {
    return true;
  }

  protected int sparkVersion() {
    return this.sparkVersion.toNumber();
  }

  protected boolean isSecretSupported() {
    return this.sparkVersion.isSecretSocketSupported();
  }

  /**
   * We can inject SparkInterpreter in the case that SparkIRInterpreter is used by
   * SparkShinyInterpreter in which case it is not in the same InterpreterGroup of
   * SparkInterpreter.
   * @param sparkInterpreter
   */
  public void setSparkInterpreter(SparkInterpreter sparkInterpreter) {
    this.sparkInterpreter = sparkInterpreter;
  }

  public void open() throws InterpreterException {
    if (sparkInterpreter == null) {
      this.sparkInterpreter = getInterpreterInTheSameSessionByClassName(SparkInterpreter.class);
    }
    this.sc = sparkInterpreter.getSparkContext();
    this.jsc = sparkInterpreter.getJavaSparkContext();
    this.sparkVersion = new SparkVersion(sc.version());
    this.isSpark2 = sparkVersion.newerThanEquals(SparkVersion.SPARK_2_0_0);

    ZeppelinRContext.setSparkContext(sc);
    ZeppelinRContext.setJavaSparkContext(jsc);
    if (isSpark2) {
      ZeppelinRContext.setSparkSession(sparkInterpreter.getSparkSession());
    }
    ZeppelinRContext.setSqlContext(sparkInterpreter.getSQLContext());
    ZeppelinRContext.setZeppelinContext(sparkInterpreter.getZeppelinContext());
    super.open();
  }

  @Override
  public InterpreterResult internalInterpret(String lines, InterpreterContext context) throws InterpreterException {
    Utils.printDeprecateMessage(sparkInterpreter.getSparkVersion(),
            context, properties);
    String jobGroup = Utils.buildJobGroupId(context);
    String jobDesc = Utils.buildJobDesc(context);
    sparkInterpreter.getSparkContext().setJobGroup(jobGroup, jobDesc, false);
    String setJobGroup = "";
    // assign setJobGroup to dummy__, otherwise it would print NULL for this statement
    if (isSpark2) {
      setJobGroup = "dummy__ <- setJobGroup(\"" + jobGroup +
              "\", \" +" + jobDesc + "\", TRUE)";
    } else {
      setJobGroup = "dummy__ <- setJobGroup(sc, \"" + jobGroup +
              "\", \"" + jobDesc + "\", TRUE)";
    }
    lines = setJobGroup + "\n" + lines;
    if (sparkInterpreter.getSparkVersion().newerThanEquals(SparkVersion.SPARK_2_3_0)) {
      // setLocalProperty is only available from spark 2.3.0
      String setPoolStmt = "setLocalProperty('spark.scheduler.pool', NULL)";
      if (context.getLocalProperties().containsKey("pool")) {
        setPoolStmt = "setLocalProperty('spark.scheduler.pool', '" +
                context.getLocalProperties().get("pool") + "')";
      }
      lines = setPoolStmt + "\n" + lines;
    }
    return super.internalInterpret(lines, context);
  }
}
