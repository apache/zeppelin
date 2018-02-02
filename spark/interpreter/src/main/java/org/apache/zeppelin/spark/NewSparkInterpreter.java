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
import org.apache.spark.scheduler.SparkListenerJobStart;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.ui.jobs.JobProgressListener;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.DefaultInterpreterProperty;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteEventClientWrapper;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.spark.dep.SparkDependencyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * SparkInterpreter of Java implementation. It is just wrapper of Spark211Interpreter
 * and Spark210Interpreter.
 */
public class NewSparkInterpreter extends AbstractSparkInterpreter {

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

  private static InterpreterHookRegistry hooks;


  public NewSparkInterpreter(Properties properties) {
    super(properties);
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
      setupConfForPySpark();
      SparkConf conf = new SparkConf();
      for (Map.Entry<Object, Object> entry : getProperties().entrySet()) {
        if (!StringUtils.isBlank(entry.getValue().toString())) {
          conf.set(entry.getKey().toString(), entry.getValue().toString());
        }
        if (entry.getKey().toString().equals("zeppelin.spark.useHiveContext")) {
          conf.set("spark.useHiveContext", entry.getValue().toString());
        }
      }
      // use local mode for embedded spark mode when spark.master is not found
      conf.setIfMissing("spark.master", "local");

      String innerIntpClassName = innerInterpreterClassMap.get(scalaVersion);
      Class clazz = Class.forName(innerIntpClassName);
      this.innerInterpreter =
          (BaseSparkScalaInterpreter) clazz.getConstructor(SparkConf.class, List.class)
              .newInstance(conf, getDependencyFiles());
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
      sparkUrl = this.innerInterpreter.sparkUrl();
      setupListeners();

      hooks = getInterpreterGroup().getInterpreterHookRegistry();
      z = new SparkZeppelinContext(sc, hooks,
          Integer.parseInt(getProperty("zeppelin.spark.maxResult")));
      this.innerInterpreter.bind("z", z.getClass().getCanonicalName(), z,
          Lists.newArrayList("@transient"));
    } catch (Exception e) {
      LOGGER.error(ExceptionUtils.getStackTrace(e));
      throw new InterpreterException("Fail to open SparkInterpreter", e);
    }
  }

  private void setupConfForPySpark() {
    String sparkHome = getProperty("SPARK_HOME");
    File pysparkFolder = null;
    if (sparkHome == null) {
      String zeppelinHome =
          new DefaultInterpreterProperty("ZEPPELIN_HOME", "zeppelin.home", "../../")
              .getValue().toString();
      pysparkFolder = new File(zeppelinHome,
          "interpreter" + File.separator + "spark" + File.separator + "pyspark");
    } else {
      pysparkFolder = new File(sparkHome, "python" + File.separator + "lib");
    }

    ArrayList<String> pysparkPackages = new ArrayList<>();
    for (File file : pysparkFolder.listFiles()) {
      if (file.getName().equals("pyspark.zip")) {
        pysparkPackages.add(file.getAbsolutePath());
      }
      if (file.getName().startsWith("py4j-")) {
        pysparkPackages.add(file.getAbsolutePath());
      }
    }

    if (pysparkPackages.size() != 2) {
      throw new RuntimeException("Not correct number of pyspark packages: " +
          StringUtils.join(pysparkPackages, ","));
    }
    // Distribute two libraries(pyspark.zip and py4j-*.zip) to workers
    System.setProperty("spark.files", mergeProperty(System.getProperty("spark.files", ""),
        StringUtils.join(pysparkPackages, ",")));
    System.setProperty("spark.submit.pyFiles", mergeProperty(
        System.getProperty("spark.submit.pyFiles", ""), StringUtils.join(pysparkPackages, ",")));

  }

  private String mergeProperty(String originalValue, String appendedValue) {
    if (StringUtils.isBlank(originalValue)) {
      return appendedValue;
    }
    return originalValue + "," + appendedValue;
  }

  @Override
  public void close() {
    LOGGER.info("Close SparkInterpreter");
    innerInterpreter.close();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    InterpreterContext.set(context);
    z.setGui(context.getGui());
    z.setNoteGui(context.getNoteGui());
    z.setInterpreterContext(context);
    populateSparkWebUrl(context);
    String jobDesc = "Started by: " + Utils.getUserName(context.getAuthenticationInfo());
    sc.setJobGroup(Utils.buildJobGroupId(context), jobDesc, false);
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

  private void setupListeners() {
    JobProgressListener pl = new JobProgressListener(sc.getConf()) {
      @Override
      public synchronized void onJobStart(SparkListenerJobStart jobStart) {
        super.onJobStart(jobStart);
        int jobId = jobStart.jobId();
        String jobGroupId = jobStart.properties().getProperty("spark.jobGroup.id");
        String uiEnabled = jobStart.properties().getProperty("spark.ui.enabled");
        String jobUrl = getJobUrl(jobId);
        String noteId = Utils.getNoteId(jobGroupId);
        String paragraphId = Utils.getParagraphId(jobGroupId);
        // Button visible if Spark UI property not set, set as invalid boolean or true
        java.lang.Boolean showSparkUI =
            uiEnabled == null || !uiEnabled.trim().toLowerCase().equals("false");
        if (showSparkUI && jobUrl != null) {
          RemoteEventClientWrapper eventClient = BaseZeppelinContext.getEventClient();
          Map<String, String> infos = new java.util.HashMap<>();
          infos.put("jobUrl", jobUrl);
          infos.put("label", "SPARK JOB");
          infos.put("tooltip", "View in Spark web UI");
          if (eventClient != null) {
            eventClient.onParaInfosReceived(noteId, paragraphId, infos);
          }
        }
      }

      private String getJobUrl(int jobId) {
        String jobUrl = null;
        if (sparkUrl != null) {
          jobUrl = sparkUrl + "/jobs/job?id=" + jobId;
        }
        return jobUrl;
      }
    };
    try {
      Object listenerBus = sc.getClass().getMethod("listenerBus").invoke(sc);
      Method[] methods = listenerBus.getClass().getMethods();
      Method addListenerMethod = null;
      for (Method m : methods) {
        if (!m.getName().equals("addListener")) {
          continue;
        }
        Class<?>[] parameterTypes = m.getParameterTypes();
        if (parameterTypes.length != 1) {
          continue;
        }
        if (!parameterTypes[0].isAssignableFrom(JobProgressListener.class)) {
          continue;
        }
        addListenerMethod = m;
        break;
      }
      if (addListenerMethod != null) {
        addListenerMethod.invoke(listenerBus, pl);
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      LOGGER.error(e.toString(), e);
    }
  }

  public SparkZeppelinContext getZeppelinContext() {
    return this.z;
  }

  public SparkContext getSparkContext() {
    return this.sc;
  }

  @Override
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

  private DepInterpreter getDepInterpreter() {
    Interpreter p = getInterpreterInTheSameSessionByClassName(DepInterpreter.class.getName());
    if (p == null) {
      return null;
    }

    while (p instanceof WrappedInterpreter) {
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    return (DepInterpreter) p;
  }

  private String extractScalaVersion() throws IOException, InterruptedException {
    String scalaVersionString = scala.util.Properties.versionString();
    if (scalaVersionString.contains("version 2.10")) {
      return "2.10";
    } else {
      return "2.11";
    }
  }

  public void populateSparkWebUrl(InterpreterContext ctx) {
    Map<String, String> infos = new java.util.HashMap<>();
    infos.put("url", sparkUrl);
    String uiEnabledProp = properties.getProperty("spark.ui.enabled", "true");
    java.lang.Boolean uiEnabled = java.lang.Boolean.parseBoolean(
        uiEnabledProp.trim());
    if (!uiEnabled) {
      infos.put("message", "Spark UI disabled");
    } else {
      if (StringUtils.isNotBlank(sparkUrl)) {
        infos.put("message", "Spark UI enabled");
      } else {
        infos.put("message", "No spark url defined");
      }
    }
    if (ctx != null && ctx.getClient() != null) {
      LOGGER.debug("Sending metadata to Zeppelin server: {}", infos.toString());
      getZeppelinContext().setEventClient(ctx.getClient());
      ctx.getClient().onMetaInfosReceived(infos);
    }
  }

  public boolean isSparkContextInitialized() {
    return this.sc != null;
  }

  private List<String> getDependencyFiles() {
    List<String> depFiles = new ArrayList<>();
    // add jar from DepInterpreter
    DepInterpreter depInterpreter = getDepInterpreter();
    if (depInterpreter != null) {
      SparkDependencyContext depc = depInterpreter.getDependencyContext();
      if (depc != null) {
        List<File> files = depc.getFilesDist();
        if (files != null) {
          for (File f : files) {
            depFiles.add(f.getAbsolutePath());
          }
        }
      }
    }

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

  @Override
  public String getSparkUIUrl() {
    return sparkUrl;
  }

  @Override
  public boolean isUnsupportedSparkVersion() {
    return enableSupportedVersionCheck  && sparkVersion.isUnsupportedVersion();
  }
}
