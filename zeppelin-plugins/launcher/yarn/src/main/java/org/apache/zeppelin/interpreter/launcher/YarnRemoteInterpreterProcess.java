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

package org.apache.zeppelin.interpreter.launcher;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.ApplicationNotFoundException;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * Start interpreter in yarn container.
 */
public class YarnRemoteInterpreterProcess extends RemoteInterpreterProcess {

  private static Logger LOGGER = LoggerFactory.getLogger(YarnRemoteInterpreterProcess.class);

  private String host;
  private int port = -1;
  private ZeppelinConfiguration zConf;
  private final InterpreterLaunchContext launchContext;
  private final Properties properties;
  private final Map<String, String> envs;
  private AtomicBoolean isYarnAppRunning = new AtomicBoolean(false);
  private String errorMessage;

  /************** Hadoop related **************************/
  private Configuration hadoopConf;
  private FileSystem fs;
  private FileSystem localFs;
  private YarnClient yarnClient;
  private ApplicationId appId;
  private Path stagingDir;

  // App files are world-wide readable and owner writable -> rw-r--r--
  private static final FsPermission APP_FILE_PERMISSION =
          FsPermission.createImmutable(Short.parseShort("644", 8));

  public YarnRemoteInterpreterProcess(
          InterpreterLaunchContext launchContext,
          Properties properties,
          Map<String, String> envs,
          int connectTimeout) {
    super(connectTimeout, launchContext.getIntpEventServerHost(), launchContext.getIntpEventServerPort());
    this.zConf = ZeppelinConfiguration.create();
    this.launchContext = launchContext;
    this.properties = properties;
    this.envs = envs;

    yarnClient = YarnClient.createYarnClient();
    this.hadoopConf = new YarnConfiguration();

    // Add core-site.xml and yarn-site.xml. This is for integration test where using MiniHadoopCluster.
    if (properties.containsKey("HADOOP_CONF_DIR") &&
            !org.apache.commons.lang3.StringUtils.isBlank(properties.getProperty("HADOOP_CONF_DIR"))) {
      File hadoopConfDir = new File(properties.getProperty("HADOOP_CONF_DIR"));
      if (hadoopConfDir.exists() && hadoopConfDir.isDirectory()) {
        File coreSite = new File(hadoopConfDir, "core-site.xml");
        try {
          this.hadoopConf.addResource(coreSite.toURI().toURL());
        } catch (MalformedURLException e) {
          LOGGER.warn("Fail to add core-site.xml: " + coreSite.getAbsolutePath(), e);
        }
        File yarnSite = new File(hadoopConfDir, "yarn-site.xml");
        try {
          this.hadoopConf.addResource(yarnSite.toURI().toURL());
        } catch (MalformedURLException e) {
          LOGGER.warn("Fail to add yarn-site.xml: " + yarnSite.getAbsolutePath(), e);
        }
      } else {
        throw new RuntimeException("HADOOP_CONF_DIR: " + hadoopConfDir.getAbsolutePath() +
                " doesn't exist or is not a directory");
      }
    }

    yarnClient.init(this.hadoopConf);
    yarnClient.start();
    try {
      this.fs = FileSystem.get(hadoopConf);
      this.localFs = FileSystem.getLocal(hadoopConf);
    } catch (IOException e) {
      throw new RuntimeException("Fail to create FileSystem", e);
    }
  }

  @Override
  public void processStarted(int port, String host) {
    this.port = port;
    this.host = host;
  }

  @Override
  public String getErrorMessage() {
    return this.errorMessage;
  }

  @Override
  public String getInterpreterGroupId() {
    return launchContext.getInterpreterGroupId();
  }

  @Override
  public String getInterpreterSettingName() {
    return launchContext.getInterpreterSettingName();
  }

  @Override
  public void start(String userName) throws IOException {
    try {
      LOGGER.info("Submitting zeppelin-interpreter app to yarn");
      final YarnClientApplication yarnApplication = yarnClient.createApplication();
      final GetNewApplicationResponse appResponse = yarnApplication.getNewApplicationResponse();
      this.appId = appResponse.getApplicationId();
      ApplicationSubmissionContext appContext = yarnApplication.getApplicationSubmissionContext();
      appContext = createApplicationSubmissionContext(appContext);
      yarnClient.submitApplication(appContext);

      long start = System.currentTimeMillis();
      ApplicationReport appReport = getApplicationReport(appId);
      while (appReport.getYarnApplicationState() != YarnApplicationState.FAILED &&
              appReport.getYarnApplicationState() != YarnApplicationState.FINISHED &&
              appReport.getYarnApplicationState() != YarnApplicationState.KILLED &&
              appReport.getYarnApplicationState() != YarnApplicationState.RUNNING) {
        LOGGER.info("Wait for zeppelin interpreter yarn app to be started");
        Thread.sleep(2000);
        if ((System.currentTimeMillis() - start) > getConnectTimeout()) {
          yarnClient.killApplication(this.appId);
          throw new IOException("Launching zeppelin interpreter in yarn is time out, kill it now");
        }
        appReport = getApplicationReport(appId);
      }

      if (appReport.getYarnApplicationState() != YarnApplicationState.RUNNING) {
        this.errorMessage = appReport.getDiagnostics();
        throw new Exception("Failed to submit application to YARN"
                + ", applicationId=" + appId
                + ", diagnostics=" + appReport.getDiagnostics());
      }
      isYarnAppRunning.set(true);

    } catch (Exception e) {
      LOGGER.error("Fail to launch yarn interpreter process", e);
      throw new IOException(e);
    } finally {
      if (stagingDir != null) {
        this.fs.delete(stagingDir, true);
      }
    }
  }

  private ApplicationReport getApplicationReport(ApplicationId appId) throws YarnException, IOException {
    ApplicationReport report = yarnClient.getApplicationReport(appId);
    if (report.getYarnApplicationState() == null) {
      // The state can be null when the ResourceManager does not know about the app but the YARN
      // application history server has an incomplete entry for it. Treat this scenario as if the
      // application does not exist, since the final app status cannot be determined. This also
      // matches the behavior for this scenario if the history server was not configured.
      throw new ApplicationNotFoundException("YARN reports no state for application "
              + appId);
    }
    return report;
  }

  private ApplicationSubmissionContext createApplicationSubmissionContext(
          ApplicationSubmissionContext appContext) throws Exception {

    setResources(appContext);
    setPriority(appContext);
    setQueue(appContext);
    appContext.setApplicationId(appId);
    setApplicationName(appContext);
    appContext.setApplicationType("ZEPPELIN INTERPRETER");
    appContext.setMaxAppAttempts(1);

    ContainerLaunchContext amContainer = setUpAMLaunchContext();
    appContext.setAMContainerSpec(amContainer);
    appContext.setCancelTokensWhenComplete(true);
    return appContext;
  }

  private ContainerLaunchContext setUpAMLaunchContext() throws IOException {
    ContainerLaunchContext amContainer = Records.newRecord(ContainerLaunchContext.class);

    // Set the resources to localize
    this.stagingDir = new Path(fs.getHomeDirectory() + "/.zeppelinStaging", appId.toString());
    Map<String, LocalResource> localResources = new HashMap<>();

    File interpreterZip = createInterpreterZip();
    Path srcPath = localFs.makeQualified(new Path(interpreterZip.toURI()));
    Path destPath = copyFileToRemote(stagingDir, srcPath, (short) 1);
    addResource(fs, destPath, localResources, LocalResourceType.ARCHIVE, "zeppelin");
    FileUtils.forceDelete(interpreterZip);

    // TODO(zjffdu) Should not add interpreter specific logic here.
    if (launchContext.getInterpreterSettingGroup().equals("flink")) {
      File flinkZip = createFlinkZip();
      srcPath = localFs.makeQualified(new Path(flinkZip.toURI()));
      destPath = copyFileToRemote(stagingDir, srcPath, (short) 1);
      addResource(fs, destPath, localResources, LocalResourceType.ARCHIVE, "flink");
      FileUtils.forceDelete(flinkZip);
    }
    amContainer.setLocalResources(localResources);

    // Setup the command to run the AM
    List<String> vargs = new ArrayList<>();
    vargs.add(ApplicationConstants.Environment.PWD.$() + "/zeppelin/bin/interpreter.sh");
    vargs.add("-d");
    vargs.add(ApplicationConstants.Environment.PWD.$() + "/zeppelin/interpreter/"
            + launchContext.getInterpreterSettingGroup());
    vargs.add("-c");
    vargs.add(launchContext.getIntpEventServerHost());
    vargs.add("-p");
    vargs.add(launchContext.getIntpEventServerPort() + "");
    vargs.add("-r");
    vargs.add(zConf.getInterpreterPortRange() + "");
    vargs.add("-i");
    vargs.add(launchContext.getInterpreterGroupId());
    vargs.add("-l");
    vargs.add(ApplicationConstants.Environment.PWD.$() + "/zeppelin/" +
            ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LOCALREPO.getStringValue()
            + "/" + launchContext.getInterpreterSettingName());
    vargs.add("-g");
    vargs.add(launchContext.getInterpreterSettingName());

    vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR +
            File.separator + ApplicationConstants.STDOUT);
    vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR +
            File.separator + ApplicationConstants.STDERR);

    // Setup ContainerLaunchContext for AM container
    amContainer.setCommands(vargs);

    // pass the interpreter ENV to yarn container and also add hadoop jars to CLASSPATH
    populateHadoopClasspath(this.envs);
    if (this.launchContext.getInterpreterSettingGroup().equals("flink")) {
      // Update the flink related env because the all these are different in yarn container
      this.envs.put("FLINK_HOME", ApplicationConstants.Environment.PWD.$() + "/flink");
      this.envs.put("FLINK_CONF_DIR", ApplicationConstants.Environment.PWD.$() + "/flink/conf");
      this.envs.put("FLINK_LIB_DIR", ApplicationConstants.Environment.PWD.$() + "/flink/lib");
      this.envs.put("FLINK_PLUGINS_DIR", ApplicationConstants.Environment.PWD.$() + "/flink/plugins");
    }
    // set -Xmx
    int memory = Integer.parseInt(
            properties.getProperty("zeppelin.interpreter.yarn.resource.memory", "1024"));
    this.envs.put("ZEPPELIN_INTP_MEM", "-Xmx" + memory + "m");
    amContainer.setEnvironment(this.envs);

    return amContainer;
  }

  /**
   * Populate the classpath entry in the given environment map with any application
   * classpath specified through the Hadoop and Yarn configurations.
   */
  private void populateHadoopClasspath(Map<String, String> envs) {
    List<String> yarnClassPath = Lists.newArrayList(getYarnAppClasspath());
    List<String> mrClassPath = Lists.newArrayList(getMRAppClasspath());
    yarnClassPath.addAll(mrClassPath);
    LOGGER.info("Adding hadoop classpath: " + org.apache.commons.lang3.StringUtils.join(yarnClassPath, ":"));
    for (String path : yarnClassPath) {
      String newValue = path;
      if (envs.containsKey(ApplicationConstants.Environment.CLASSPATH.name())) {
        newValue = envs.get(ApplicationConstants.Environment.CLASSPATH.name()) +
                ApplicationConstants.CLASS_PATH_SEPARATOR + newValue;
      }
      envs.put(ApplicationConstants.Environment.CLASSPATH.name(), newValue);
    }
  }

  private String[] getYarnAppClasspath() {
    String[] classpaths = hadoopConf.getStrings(YarnConfiguration.YARN_APPLICATION_CLASSPATH);
    if (classpaths == null || classpaths.length == 0) {
      return getDefaultYarnApplicationClasspath();
    } else {
      return classpaths;
    }
  }

  private String[] getMRAppClasspath() {
    String[] classpaths = hadoopConf.getStrings("mapreduce.application.classpath");
    if (classpaths == null || classpaths.length == 0) {
      return getDefaultMRApplicationClasspath();
    } else {
      return classpaths;
    }
  }

  private String[] getDefaultYarnApplicationClasspath() {
    return YarnConfiguration.DEFAULT_YARN_APPLICATION_CLASSPATH;
  }

  private String[] getDefaultMRApplicationClasspath() {
    return StringUtils.getStrings(MRJobConfig.DEFAULT_MAPREDUCE_APPLICATION_CLASSPATH);
  }

  private void setResources(ApplicationSubmissionContext appContext) {
    int memory = Integer.parseInt(
            properties.getProperty("zeppelin.interpreter.yarn.resource.memory", "1024"));
    int memoryOverHead = Integer.parseInt(
            properties.getProperty("zeppelin.interpreter.yarn.resource.memoryOverhead", "384"));
    if (memoryOverHead < memory * 0.1) {
      memoryOverHead = 384;
    }
    int cores = Integer.parseInt(
            properties.getProperty("zeppelin.interpreter.yarn.resource.cores", "1"));
    final Resource resource = Resource.newInstance(memory + memoryOverHead, cores);
    appContext.setResource(resource);
  }

  private void setPriority(ApplicationSubmissionContext appContext) {
    Priority pri = Records.newRecord(Priority.class);
    pri.setPriority(1);
    appContext.setPriority(pri);
  }

  private void setQueue(ApplicationSubmissionContext appContext) {
    String queue = properties.getProperty("zeppelin.interpreter.yarn.queue", "default");
    appContext.setQueue(queue);
  }

  private void setApplicationName(ApplicationSubmissionContext appContext) {
    appContext.setApplicationName("Zeppelin Interpreter " + launchContext.getInterpreterGroupId());
  }

  /**
   * @param zos
   * @param srcFile
   * @param parentDirectoryName
   * @throws IOException
   */
  private void addFileToZipStream(ZipOutputStream zos,
                                  File srcFile,
                                  String parentDirectoryName) throws IOException {
    if (srcFile == null || !srcFile.exists()) {
      return;
    }

    String zipEntryName = srcFile.getName();
    if (parentDirectoryName != null && !parentDirectoryName.isEmpty()) {
      zipEntryName = parentDirectoryName + "/" + srcFile.getName();
    }

    if (srcFile.isDirectory()) {
      for (File file : srcFile.listFiles()) {
        addFileToZipStream(zos, file, zipEntryName);
      }
    } else {
      zos.putNextEntry(new ZipEntry(zipEntryName));
      Files.copy(srcFile, zos);
      zos.closeEntry();
    }
  }

  /**
   *
   * Create zip file to interpreter.
   * The contents are all the stuff under ZEPPELIN_HOME/interpreter/{interpreter_name}
   * @return
   * @throws IOException
   */
  private File createInterpreterZip() throws IOException {
    File interpreterArchive = File.createTempFile("zeppelin_interpreter_", ".zip", Files.createTempDir());
    ZipOutputStream interpreterZipStream = new ZipOutputStream(new FileOutputStream(interpreterArchive));
    interpreterZipStream.setLevel(0);

    String zeppelinHomeEnv = System.getenv("ZEPPELIN_HOME");
    if (org.apache.commons.lang3.StringUtils.isBlank(zeppelinHomeEnv)) {
      throw new IOException("ZEPPELIN_HOME is not specified");
    }
    File zeppelinHome = new File(zeppelinHomeEnv);
    File binDir = new File(zeppelinHome, "bin");
    addFileToZipStream(interpreterZipStream, binDir, null);

    File confDir = new File(zeppelinHome, "conf");
    addFileToZipStream(interpreterZipStream, confDir, null);

    File interpreterDir = new File(zeppelinHome, "interpreter/" + launchContext.getInterpreterSettingGroup());
    addFileToZipStream(interpreterZipStream, interpreterDir, "interpreter");

    File localRepoDir = new File(zConf.getInterpreterLocalRepoPath() + "/"
            + launchContext.getInterpreterSettingName());
    if (localRepoDir.exists() && localRepoDir.isDirectory()) {
      LOGGER.debug("Adding localRepoDir {} to interpreter zip: ", localRepoDir.getAbsolutePath());
      addFileToZipStream(interpreterZipStream, localRepoDir, "local-repo");
    }

    // add zeppelin-interpreter-shaded jar
    File[] interpreterShadedFiles = new File(zeppelinHome, "interpreter").listFiles(
            file -> file.getName().startsWith("zeppelin-interpreter-shaded")
                    && file.getName().endsWith(".jar"));
    if (interpreterShadedFiles.length == 0) {
      throw new IOException("No zeppelin-interpreter-shaded jar found under " +
              zeppelinHome.getAbsolutePath() + "/interpreter");
    }
    if (interpreterShadedFiles.length > 1) {
      throw new IOException("More than 1 zeppelin-interpreter-shaded jars found under "
              + zeppelinHome.getAbsolutePath() + "/interpreter");
    }
    addFileToZipStream(interpreterZipStream, interpreterShadedFiles[0], "interpreter");

    interpreterZipStream.flush();
    interpreterZipStream.close();
    return interpreterArchive;
  }

  private File createFlinkZip() throws IOException {
    File flinkArchive = File.createTempFile("flink_", ".zip", Files.createTempDir());
    ZipOutputStream flinkZipStream = new ZipOutputStream(new FileOutputStream(flinkArchive));
    flinkZipStream.setLevel(0);

    String flinkHomeEnv = envs.get("FLINK_HOME");
    File flinkHome = new File(flinkHomeEnv);
    if (!flinkHome.exists() || !flinkHome.isDirectory()) {
      throw new IOException("FLINK_HOME " + flinkHome.getAbsolutePath() +
              " doesn't exist or is not a directory.");
    }
    for (File file : flinkHome.listFiles()) {
      addFileToZipStream(flinkZipStream, file, null);
    }

    flinkZipStream.flush();
    flinkZipStream.close();
    return flinkArchive;
  }

  private Path copyFileToRemote(
          Path destDir,
          Path srcPath,
          Short replication) throws IOException {
    FileSystem destFs = destDir.getFileSystem(hadoopConf);
    FileSystem srcFs = srcPath.getFileSystem(hadoopConf);

    Path destPath = new Path(destDir, srcPath.getName());
    LOGGER.info("Uploading resource " + srcPath + " to " + destPath);
    FileUtil.copy(srcFs, srcPath, destFs, destPath, false, hadoopConf);
    destFs.setReplication(destPath, replication);
    destFs.setPermission(destPath, APP_FILE_PERMISSION);

    return destPath;
  }

  private void addResource(
          FileSystem fs,
          Path destPath,
          Map<String, LocalResource> localResources,
          LocalResourceType resourceType,
          String link) throws IOException {

    FileStatus destStatus = fs.getFileStatus(destPath);
    LocalResource amJarRsrc = Records.newRecord(LocalResource.class);
    amJarRsrc.setType(resourceType);
    amJarRsrc.setVisibility(LocalResourceVisibility.PUBLIC);
    amJarRsrc.setResource(ConverterUtils.getYarnUrlFromPath(destPath));
    amJarRsrc.setTimestamp(destStatus.getModificationTime());
    amJarRsrc.setSize(destStatus.getLen());
    localResources.put(link, amJarRsrc);
  }

  @Override
  public void stop() {
    if (isRunning()) {
      LOGGER.info("Kill interpreter process");
      try {
        callRemoteFunction(client -> {
          client.shutdown();
          return null;
        });
      } catch (Exception e) {
        LOGGER.warn("ignore the exception when shutting down", e);
      }

      // Shutdown connection
      shutdown();
    }

    yarnClient.stop();
    LOGGER.info("Remote process terminated");
  }

  @Override
  public String getHost() {
    return this.host;
  }

  @Override
  public int getPort() {
    return this.port;
  }

  @Override
  public boolean isRunning() {
    return isYarnAppRunning.get();
  }
}
