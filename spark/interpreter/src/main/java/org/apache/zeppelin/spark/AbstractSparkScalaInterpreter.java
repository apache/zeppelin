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
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.SparkJobInfo;
import org.apache.spark.SparkStageInfo;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SparkSession;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.kotlin.KotlinInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This is bridge class which bridge the communication between java side and scala side.
 * Java side reply on this abstract class which is implemented by different scala versions.
 */
public abstract class AbstractSparkScalaInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSparkScalaInterpreter.class);
  private static final AtomicInteger SESSION_NUM = new AtomicInteger(0);

  protected SparkConf conf;
  protected SparkContext sc;
  protected SparkSession sparkSession;
  protected SQLContext sqlContext;
  protected String sparkUrl;
  protected ZeppelinContext z;

  protected Properties properties;
  protected List<String> depFiles;

  public AbstractSparkScalaInterpreter(SparkConf conf,
                                       Properties properties,
                                       List<String> depFiles) {
    this.conf = conf;
    this.properties = properties;
    this.depFiles = depFiles;
  }

  public SparkContext getSparkContext() {
    return this.sc;
  }

  public SQLContext getSqlContext() {
    return this.sqlContext;
  }

  public SparkSession getSparkSession() {
    return this.sparkSession;
  }

  public String getSparkUrl() {
    return this.sparkUrl;
  }

  public ZeppelinContext getZeppelinContext() {
    return this.z;
  }

  public AbstractSparkScalaInterpreter() {
  }

  public void open() throws InterpreterException {
    /* Required for scoped mode.
     * In scoped mode multiple scala compiler (repl) generates class in the same directory.
     * Class names is not randomly generated and look like '$line12.$read$$iw$$iw'
     * Therefore it's possible to generated class conflict(overwrite) with other repl generated
     * class.
     *
     * To prevent generated class name conflict,
     * change prefix of generated class name from each scala compiler (repl) instance.
     *
     * In Spark 2.x, REPL generated wrapper class name should compatible with the pattern
     * ^(\$line(?:\d+)\.\$read)(?:\$\$iw)+$
     *
     * As hashCode() can return a negative integer value and the minus character '-' is invalid
     * in a package name we change it to a numeric value '0' which still conforms to the regexp.
     *
     */
    System.setProperty("scala.repl.name.line", ("$line" + this.hashCode()).replace('-', '0'));
    SESSION_NUM.incrementAndGet();

    createSparkILoop();
    createSparkContext();
    createZeppelinContext();
  }

  public void close() throws InterpreterException {
    // delete stagingDir for yarn mode
    if (getSparkMaster().startsWith("yarn")) {
      YarnConfiguration hadoopConf = new YarnConfiguration();
      Path appStagingBaseDir = null;
      if (conf.contains("spark.yarn.stagingDir")) {
        appStagingBaseDir = new Path(conf.get("spark.yarn.stagingDir"));
      } else {
        try {
          appStagingBaseDir = FileSystem.get(hadoopConf).getHomeDirectory();
        } catch (IOException e) {
          LOGGER.error("Fail to get stagingBaseDir", e);
        }
      }
      if (appStagingBaseDir != null) {
        Path stagingDirPath = new Path(appStagingBaseDir, ".sparkStaging" + "/" + sc.applicationId());
        cleanupStagingDirInternal(stagingDirPath, hadoopConf);
      }
    }

    if (sc != null) {
      sc.stop();
      sc = null;
    }
    if (sparkSession != null) {
      sparkSession.stop();
      sparkSession = null;
    }
    sqlContext = null;
    z = null;
  }

  public abstract void createSparkILoop() throws InterpreterException;

  public abstract void createZeppelinContext() throws InterpreterException;

  public void cancel(InterpreterContext context) throws InterpreterException {
    getSparkContext().cancelJobGroup(Utils.buildJobGroupId(context));
  }

  public abstract InterpreterResult interpret(String st,
                                              InterpreterContext context) throws InterpreterException;

  public abstract InterpreterResult delegateInterpret(KotlinInterpreter kotlinInterpreter,
                                                      String st,
                                                      InterpreterContext context) throws InterpreterException;

  public abstract List<InterpreterCompletion> completion(String buf,
                                                         int cursor,
                                                         InterpreterContext interpreterContext) throws InterpreterException;

  public abstract void bind(String name,
                            String tpe,
                            Object value,
                            List<String> modifier);

  // throw exception when fail to execute the code in scala shell, only used in initialization.
  // not used t run user code.
  public abstract void scalaInterpretQuietly(String code) throws InterpreterException;

  public abstract ClassLoader getScalaShellClassLoader();

  private List<String> getUserFiles() {
    return depFiles.stream()
            .filter(f -> f.endsWith(".jar"))
            .collect(Collectors.toList());
  }

  private void createSparkContext() throws InterpreterException {
    SparkSession.Builder builder = SparkSession.builder().config(conf);
    if (conf.get("spark.sql.catalogImplementation", "in-memory").equalsIgnoreCase("hive")
            || conf.get("zeppelin.spark.useHiveContext", "false").equalsIgnoreCase("true")) {
      boolean hiveSiteExisted =
              Thread.currentThread().getContextClassLoader().getResource("hive-site.xml") != null;
      if (hiveSiteExisted && hiveClassesArePresent()) {
        sparkSession = builder.enableHiveSupport().getOrCreate();
        LOGGER.info("Created Spark session (with Hive support)");
      } else {
        if (!hiveClassesArePresent()) {
          LOGGER.warn("Hive support can not be enabled because spark is not built with hive");
        }
        if (!hiveSiteExisted) {
          LOGGER.warn("Hive support can not be enabled because no hive-site.xml found");
        }
        sparkSession = builder.getOrCreate();
        LOGGER.info("Created Spark session (without Hive support)");
      }
    } else {
      sparkSession = builder.getOrCreate();
      LOGGER.info("Created Spark session (without Hive support)");
    }

    sc = sparkSession.sparkContext();
    getUserFiles().forEach(file -> sc.addFile(file));
    if (sc.uiWebUrl().isDefined()) {
      sparkUrl = sc.uiWebUrl().get();
    }
    sqlContext = sparkSession.sqlContext();

    initAndSendSparkWebUrl();

    bind("spark", sparkSession.getClass().getCanonicalName(), sparkSession, Lists.newArrayList("@transient"));
    bind("sc", "org.apache.spark.SparkContext", sc, Lists.newArrayList("@transient"));
    bind("sqlContext", "org.apache.spark.sql.SQLContext", sqlContext, Lists.newArrayList("@transient"));

    scalaInterpretQuietly("import org.apache.spark.SparkContext._");
    scalaInterpretQuietly("import spark.implicits._");
    scalaInterpretQuietly("import sqlContext.implicits._");
    scalaInterpretQuietly("import spark.sql");
    scalaInterpretQuietly("import org.apache.spark.sql.functions._");
    // print empty string otherwise the last statement's output of this method
    // (aka. import org.apache.spark.sql.functions._) will mix with the output of user code
    scalaInterpretQuietly("print(\"\")");
  }

  /**
   * @return true if Hive classes can be loaded, otherwise false.
   */
  private boolean hiveClassesArePresent() {
    try {
      Class.forName("org.apache.spark.sql.hive.HiveSessionStateBuilder");
      Class.forName("org.apache.hadoop.hive.conf.HiveConf");
      return true;
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      return false;
    }
  }

  private void initAndSendSparkWebUrl() {
    String webUiUrl = properties.getProperty("zeppelin.spark.uiWebUrl");
    if (!StringUtils.isBlank(webUiUrl)) {
      this.sparkUrl = webUiUrl.replace("{{applicationId}}", sc.applicationId());
    } else {
      useYarnProxyURLIfNeeded();
    }
    InterpreterContext.get().getIntpEventClient().sendWebUrlInfo(this.sparkUrl);
  }

  private String getSparkMaster() {
    if (conf == null) {
      return "";
    } else {
      return conf.get(SparkStringConstants.MASTER_PROP_NAME,
              SparkStringConstants.DEFAULT_MASTER_VALUE);
    }
  }

  private void cleanupStagingDirInternal(Path stagingDirPath, Configuration hadoopConf) {
    try {
      FileSystem fs = stagingDirPath.getFileSystem(hadoopConf);
      if (fs.delete(stagingDirPath, true)) {
        LOGGER.info("Deleted staging directory " + stagingDirPath);
      }
    } catch (IOException e) {
      LOGGER.warn("Failed to cleanup staging dir " + stagingDirPath, e);
    }
  }

  private void useYarnProxyURLIfNeeded() {
    if (Boolean.parseBoolean(properties.getProperty("spark.webui.yarn.useProxy", "false"))) {
      if (getSparkMaster().startsWith("yarn")) {
        String appId = sc.applicationId();
        YarnClient yarnClient = YarnClient.createYarnClient();
        YarnConfiguration yarnConf = new YarnConfiguration();
        // disable timeline service as we only query yarn app here.
        // Otherwise we may hit this kind of ERROR:
        // java.lang.ClassNotFoundException: com.sun.jersey.api.client.config.ClientConfig
        yarnConf.set("yarn.timeline-service.enabled", "false");
        yarnClient.init(yarnConf);
        yarnClient.start();
        ApplicationReport appReport = null;
        try {
          appReport = yarnClient.getApplicationReport(ConverterUtils.toApplicationId(appId));
          this.sparkUrl = appReport.getTrackingUrl();
        } catch (YarnException | IOException e) {
          LOGGER.error("Fail to get yarn app report", e);
        }
      }
    }
  }

  public int getProgress(InterpreterContext context) throws InterpreterException {
    String jobGroup = Utils.buildJobGroupId(context);
    // Each paragraph has one unique jobGroup, and one paragraph may run multiple times.
    // So only look for the first job which match the jobGroup
    Optional<SparkJobInfo> jobInfoOptional = Arrays.stream(sc.statusTracker().getJobIdsForGroup(jobGroup))
            .mapToObj(jobId -> sc.statusTracker().getJobInfo(jobId))
            .filter(jobInfo -> jobInfo.isDefined())
            .map(jobInfo -> jobInfo.get())
            .findFirst();
    if (jobInfoOptional.isPresent()) {
      List<SparkStageInfo> stageInfoList = Arrays.stream(jobInfoOptional.get().stageIds())
              .mapToObj(stageId -> sc.statusTracker().getStageInfo(stageId))
              .filter(stageInfo -> stageInfo.isDefined())
              .map(stageInfo -> stageInfo.get())
              .collect(Collectors.toList());
      int taskCount = stageInfoList.stream()
              .map(stageInfo -> stageInfo.numTasks())
              .collect(Collectors.summingInt(Integer::intValue));
      int completedTaskCount = stageInfoList.stream()
              .map(stageInfo -> stageInfo.numCompletedTasks())
              .collect(Collectors.summingInt(Integer::intValue));
      LOGGER.debug("Total TaskCount: " + taskCount);
      LOGGER.debug("Completed TaskCount: " + completedTaskCount);
      if (taskCount == 0) {
        return 0;
      } else {
        return 100 * completedTaskCount / taskCount;
      }
    } else {
      return 0;
    }
  }
}
