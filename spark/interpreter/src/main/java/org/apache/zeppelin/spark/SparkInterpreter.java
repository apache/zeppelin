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

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * SparkInterpreter of Java implementation. It is just wrapper of Spark211Interpreter
 * and Spark210Interpreter.
 */
public class SparkInterpreter extends AbstractInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkInterpreter.class);

  private BaseSparkScalaInterpreter innerInterpreter;
  private Map<String, String> innerInterpreterClassMap = new HashMap<>();
  private SparkContext sc;
  private JavaSparkContext jsc;
  private SQLContext sqlContext;
  private Object sparkSession;

  private SparkZeppelinContext z;
  private SparkVersion sparkVersion;
  private boolean enableSupportedVersionCheck;
  private String sparkUrl;
  private SparkShims sparkShims;

  private static InterpreterHookRegistry hooks;


  public SparkInterpreter(Properties properties) {
    super(properties);
    // set scala.color
    if (Boolean.parseBoolean(properties.getProperty("zeppelin.spark.scala.color", "true"))) {
      System.setProperty("scala.color", "true");
    }
    this.enableSupportedVersionCheck = java.lang.Boolean.parseBoolean(
        properties.getProperty("zeppelin.spark.enableSupportedVersionCheck", "true"));
    innerInterpreterClassMap.put("2.10", "org.apache.zeppelin.spark.SparkScala210Interpreter");
    innerInterpreterClassMap.put("2.11", "org.apache.zeppelin.spark.SparkScala211Interpreter");
  }

  @Override
  public void open() throws InterpreterException {
    try {
      String scalaVersion = extractScalaVersion();
      LOGGER.info("Using Scala Version: " + scalaVersion);
      SparkConf conf = new SparkConf();
      for (Map.Entry<Object, Object> entry : getProperties().entrySet()) {
        if (!StringUtils.isBlank(entry.getValue().toString())) {
          conf.set(entry.getKey().toString(), entry.getValue().toString());
        }
        // zeppelin.spark.useHiveContext & zeppelin.spark.concurrentSQL are legacy zeppelin
        // properties, convert them to spark properties here.
        if (entry.getKey().toString().equals("zeppelin.spark.useHiveContext")) {
          conf.set("spark.useHiveContext", entry.getValue().toString());
        }
        if (entry.getKey().toString().equals("zeppelin.spark.concurrentSQL")
            && entry.getValue().toString().equals("true")) {
          conf.set("spark.scheduler.mode", "FAIR");
        }
      }
      // use local mode for embedded spark mode when spark.master is not found
      conf.setIfMissing("spark.master", "local");

      String innerIntpClassName = innerInterpreterClassMap.get(scalaVersion);
      Class clazz = Class.forName(innerIntpClassName);
      this.innerInterpreter = (BaseSparkScalaInterpreter)
          clazz.getConstructor(SparkConf.class, List.class, Boolean.class)
              .newInstance(conf, getDependencyFiles(),
                  Boolean.parseBoolean(getProperty("zeppelin.spark.printREPLOutput", "true")));
      this.innerInterpreter.open();

      sc = this.innerInterpreter.sc();
      jsc = JavaSparkContext.fromSparkContext(sc);
      sparkVersion = SparkVersion.fromVersionString(sc.version());
      if (enableSupportedVersionCheck && sparkVersion.isUnsupportedVersion()) {
        throw new Exception("This is not officially supported spark version: " + sparkVersion
            + "\nYou can set zeppelin.spark.enableSupportedVersionCheck to false if you really" +
            " want to try this version of spark.");
      }
      sqlContext = this.innerInterpreter.sqlContext();
      sparkSession = this.innerInterpreter.sparkSession();
      hooks = getInterpreterGroup().getInterpreterHookRegistry();
      sparkUrl = this.innerInterpreter.sparkUrl();
      String sparkUrlProp = getProperty("zeppelin.spark.uiWebUrl", "");
      if (!StringUtils.isBlank(sparkUrlProp)) {
        sparkUrl = sparkUrlProp;
      }
      sparkShims = SparkShims.getInstance(sc.version(), getProperties());
      sparkShims.setupSparkListener(sc.master(), sparkUrl, InterpreterContext.get());

      z = new SparkZeppelinContext(sc, sparkShims, hooks,
          Integer.parseInt(getProperty("zeppelin.spark.maxResult")));
      this.innerInterpreter.bind("z", z.getClass().getCanonicalName(), z,
          Lists.newArrayList("@transient"));
    } catch (Exception e) {
      LOGGER.error("Fail to open SparkInterpreter", ExceptionUtils.getStackTrace(e));
      throw new InterpreterException("Fail to open SparkInterpreter", e);
    }
  }

  @Override
  public void close() {
    LOGGER.info("Close SparkInterpreter");
    if (innerInterpreter != null) {
      innerInterpreter.close();
      innerInterpreter = null;
    }
  }

  @Override
  public InterpreterResult internalInterpret(String st, InterpreterContext context) {
    context.out.clear();
    sc.setJobGroup(Utils.buildJobGroupId(context), Utils.buildJobDesc(context), false);
    // set spark.scheduler.pool to null to clear the pool assosiated with this paragraph
    // sc.setLocalProperty("spark.scheduler.pool", null) will clean the pool
    sc.setLocalProperty("spark.scheduler.pool", context.getLocalProperties().get("pool"));

    return innerInterpreter.interpret(st, context);
  }

  @Override
  public void cancel(InterpreterContext context) {
    sc.cancelJobGroup(Utils.buildJobGroupId(context));
  }

  @Override
  public List<InterpreterCompletion> completion(String buf,
                                                int cursor,
                                                InterpreterContext interpreterContext) {
    LOGGER.debug("buf: " + buf + ", cursor:" + cursor);
    return innerInterpreter.completion(buf, cursor, interpreterContext);
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return innerInterpreter.getProgress(Utils.buildJobGroupId(context), context);
  }

  public SparkZeppelinContext getZeppelinContext() {
    return this.z;
  }

  public SparkContext getSparkContext() {
    return this.sc;
  }

  public SQLContext getSQLContext() {
    return sqlContext;
  }

  public JavaSparkContext getJavaSparkContext() {
    return this.jsc;
  }

  public Object getSparkSession() {
    return sparkSession;
  }

  public SparkVersion getSparkVersion() {
    return sparkVersion;
  }

  private String extractScalaVersion() throws IOException, InterruptedException {
    String scalaVersionString = scala.util.Properties.versionString();
    if (scalaVersionString.contains("version 2.10")) {
      return "2.10";
    } else {
      return "2.11";
    }
  }

  public boolean isSparkContextInitialized() {
    return this.sc != null;
  }

  private List<String> getDependencyFiles() throws InterpreterException {
    List<String> depFiles = new ArrayList<>();
    // add jar from local repo
    String localRepo = getProperty("zeppelin.interpreter.localRepo");
    if (localRepo != null) {
      File localRepoDir = new File(localRepo);
      if (localRepoDir.exists()) {
        File[] files = localRepoDir.listFiles();
        if (files != null) {
          for (File f : files) {
            depFiles.add(f.getAbsolutePath());
          }
        }
      }
    }
    return depFiles;
  }

  public String getSparkUIUrl() {
    return sparkUrl;
  }

  public boolean isUnsupportedSparkVersion() {
    return enableSupportedVersionCheck  && sparkVersion.isUnsupportedVersion();
  }
}
