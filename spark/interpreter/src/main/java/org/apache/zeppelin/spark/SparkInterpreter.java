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

import org.apache.commons.lang.StringUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SparkInterpreter of Java implementation. It delegates to different scala version AbstractSparkScalaInterpreter.
 *
 */
public class SparkInterpreter extends AbstractInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkInterpreter.class);

  private static AtomicInteger SESSION_NUM = new AtomicInteger(0);
  private AbstractSparkScalaInterpreter innerInterpreter;
  private Map<String, String> innerInterpreterClassMap = new HashMap<>();
  private SparkContext sc;
  private JavaSparkContext jsc;
  private SQLContext sqlContext;
  private Object sparkSession;

  private SparkVersion sparkVersion;
  private boolean enableSupportedVersionCheck;

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
    innerInterpreterClassMap.put("2.12", "org.apache.zeppelin.spark.SparkScala212Interpreter");
  }

  @Override
  public void open() throws InterpreterException {
    try {
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
      this.innerInterpreter = loadSparkScalaInterpreter(conf);
      this.innerInterpreter.open();

      sc = this.innerInterpreter.getSparkContext();
      jsc = JavaSparkContext.fromSparkContext(sc);
      sparkVersion = SparkVersion.fromVersionString(sc.version());
      if (enableSupportedVersionCheck && sparkVersion.isUnsupportedVersion()) {
        throw new Exception("This is not officially supported spark version: " + sparkVersion
            + "\nYou can set zeppelin.spark.enableSupportedVersionCheck to false if you really" +
            " want to try this version of spark.");
      }
      sqlContext = this.innerInterpreter.getSqlContext();
      sparkSession = this.innerInterpreter.getSparkSession();

      SESSION_NUM.incrementAndGet();
    } catch (Exception e) {
      LOGGER.error("Fail to open SparkInterpreter", e);
      throw new InterpreterException("Fail to open SparkInterpreter", e);
    }
  }

  /**
   * Load AbstractSparkScalaInterpreter based on the runtime scala version.
   * Load AbstractSparkScalaInterpreter from the following location:
   *
   * SparkScala210Interpreter   ZEPPELIN_HOME/interpreter/spark/scala-2.10
   * SparkScala211Interpreter   ZEPPELIN_HOME/interpreter/spark/scala-2.11
   * SparkScala212Interpreter   ZEPPELIN_HOME/interpreter/spark/scala-2.12
   *
   * @param conf
   * @return AbstractSparkScalaInterpreter
   * @throws Exception
   */
  private AbstractSparkScalaInterpreter loadSparkScalaInterpreter(SparkConf conf) throws Exception {
    String scalaVersion = extractScalaVersion();
    ClassLoader scalaInterpreterClassLoader = Thread.currentThread().getContextClassLoader();

    String zeppelinHome = System.getenv("ZEPPELIN_HOME");
    if (zeppelinHome != null) {
      // ZEPPELIN_HOME is null in yarn-cluster mode, load it directly via current ClassLoader.
      // otherwise, load from the specific folder ZEPPELIN_HOME/interpreter/spark/scala-<version>

      File scalaJarFolder = new File(zeppelinHome + "/interpreter/spark/scala-" + scalaVersion);
      List<URL> urls = new ArrayList<>();
      for (File file : scalaJarFolder.listFiles()) {
        LOGGER.debug("Add file " + file.getAbsolutePath() + " to classpath of spark scala interpreter: "
                + scalaJarFolder);
        urls.add(file.toURI().toURL());
      }
      scalaInterpreterClassLoader = new URLClassLoader(urls.toArray(new URL[0]),
              Thread.currentThread().getContextClassLoader());
    }

    String innerIntpClassName = innerInterpreterClassMap.get(scalaVersion);
    Class clazz = scalaInterpreterClassLoader.loadClass(innerIntpClassName);
    return (AbstractSparkScalaInterpreter)
            clazz.getConstructor(SparkConf.class, List.class, Properties.class, InterpreterGroup.class, URLClassLoader.class)
                    .newInstance(conf, getDependencyFiles(), getProperties(), getInterpreterGroup(), scalaInterpreterClassLoader);
  }

  @Override
  public void close() throws InterpreterException {
    LOGGER.info("Close SparkInterpreter");
    if (SESSION_NUM.decrementAndGet() == 0 && innerInterpreter != null) {
      innerInterpreter.close();
      innerInterpreter = null;
    }
  }

  @Override
  public InterpreterResult internalInterpret(String st,
                                             InterpreterContext context) throws InterpreterException {
    context.out.clear();
    sc.setJobGroup(Utils.buildJobGroupId(context), Utils.buildJobDesc(context), false);
    // set spark.scheduler.pool to null to clear the pool assosiated with this paragraph
    // sc.setLocalProperty("spark.scheduler.pool", null) will clean the pool
    sc.setLocalProperty("spark.scheduler.pool", context.getLocalProperties().get("pool"));

    return innerInterpreter.interpret(st, context);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    innerInterpreter.cancel(context);
  }

  @Override
  public List<InterpreterCompletion> completion(String buf,
                                                int cursor,
                                                InterpreterContext interpreterContext) throws InterpreterException {
    return innerInterpreter.completion(buf, cursor, interpreterContext);
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return innerInterpreter.getProgress(Utils.buildJobGroupId(context), context);
  }

  public ZeppelinContext getZeppelinContext() {
    return this.innerInterpreter.getZeppelinContext();
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

  private String extractScalaVersion() throws InterpreterException {
    String scalaVersionString = scala.util.Properties.versionString();
    LOGGER.info("Using Scala: " + scalaVersionString);
    if (scalaVersionString.contains("version 2.10")) {
      return "2.10";
    } else if (scalaVersionString.contains("version 2.11")) {
      return "2.11";
    } else if (scalaVersionString.contains("version 2.12")) {
      return "2.12";
    } else {
      throw new InterpreterException("Unsupported scala version: " + scalaVersionString);
    }
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

  public boolean isUnsupportedSparkVersion() {
    return enableSupportedVersionCheck  && sparkVersion.isUnsupportedVersion();
  }
}
