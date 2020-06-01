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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ExecCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.ProgressMessage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.launcher.utils.TarFileEntry;
import org.apache.zeppelin.interpreter.launcher.utils.TarUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_KEYTAB;

public class DockerInterpreterProcess extends RemoteInterpreterProcess {
  private static final Logger LOGGER = LoggerFactory.getLogger(DockerInterpreterLauncher.class);

  private String dockerIntpServicePort = "0";

  private final String interpreterGroupId;
  private final String interpreterGroupName;
  private final String interpreterSettingName;
  private final String containerImage;
  private final Properties properties;
  private final Map<String, String> envs;

  private AtomicBoolean dockerStarted = new AtomicBoolean(false);

  private DockerClient docker = null;
  private final String containerName;
  private String containerHost = "";
  private int containerPort = 0;
  private static final String DOCKER_INTP_JINJA = "/jinja_templates/docker-interpreter.jinja";

  // Upload local zeppelin library to container, There are several benefits
  // Avoid the difference between the zeppelin version and the local in the container
  // 1. RemoteInterpreterServer::main(String[] args), Different versions of args may be different
  // 2. bin/interpreter.sh Start command args may be different
  // 3. In the debugging phase for easy updating, Upload the local library file to container
  @VisibleForTesting
  boolean uploadLocalLibToContainter = true;

  private ZeppelinConfiguration zconf;

  private String zeppelinHome;

  @VisibleForTesting
  final String CONTAINER_SPARK_HOME;

  @VisibleForTesting
  final String DOCKER_HOST;

  private String containerId;
  final String CONTAINER_UPLOAD_TAR_DIR = "/tmp/zeppelin-tar";

  public DockerInterpreterProcess(
      ZeppelinConfiguration zconf,
      String containerImage,
      String interpreterGroupId,
      String interpreterGroupName,
      String interpreterSettingName,
      Properties properties,
      Map<String, String> envs,
      String intpEventServerHost,
      int intpEventServerPort,
      int connectTimeout
  ) {
    super(connectTimeout, intpEventServerHost, intpEventServerPort);

    this.containerImage = containerImage;
    this.interpreterGroupId = interpreterGroupId;
    this.interpreterGroupName = interpreterGroupName;
    this.interpreterSettingName = interpreterSettingName;
    this.properties = properties;
    this.envs = new HashMap(envs);

    this.zconf = zconf;
    this.containerName = interpreterGroupId.toLowerCase();

    String sparkHome = System.getenv("CONTAINER_SPARK_HOME");
    CONTAINER_SPARK_HOME = (sparkHome == null) ?  "/spark" : sparkHome;

    String uploadLocalLib = System.getenv("UPLOAD_LOCAL_LIB_TO_CONTAINTER");
    if (null != uploadLocalLib && StringUtils.equals(uploadLocalLib, "false")) {
      uploadLocalLibToContainter = false;
    }

    try {
      this.zeppelinHome = getZeppelinHome();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
    String defDockerHost = "http://0.0.0.0:2375";
    String dockerHost = System.getenv("DOCKER_HOST");
    DOCKER_HOST = (dockerHost == null) ?  defDockerHost : dockerHost;
  }

  @Override
  public String getInterpreterGroupId() {
    return interpreterGroupId;
  }

  @Override
  public String getInterpreterSettingName() {
    return interpreterSettingName;
  }

  @Override
  public void start(String userName) throws IOException {
    docker = DefaultDockerClient.builder().uri(URI.create(DOCKER_HOST)).build();

    removeExistContainer(containerName);

    final Map<String, List<PortBinding>> portBindings = new HashMap<>();

    // Bind container ports to host ports
    int intpServicePort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
    this.dockerIntpServicePort = String.valueOf(intpServicePort);
    final String[] ports = {dockerIntpServicePort};
    for (String port : ports) {
      List<PortBinding> hostPorts = new ArrayList<>();
      hostPorts.add(PortBinding.of("0.0.0.0", port));
      portBindings.put(port, hostPorts);
    }

    final HostConfig hostConfig = HostConfig.builder()
        .networkMode("host").portBindings(portBindings).build();

    DockerSpecTemplate specTemplate = new DockerSpecTemplate();
    specTemplate.loadProperties(getTemplateBindings());
    URL urlTemplate = this.getClass().getResource(DOCKER_INTP_JINJA);
    String template = Resources.toString(urlTemplate, Charsets.UTF_8);
    String dockerCommand = specTemplate.render(template);
    int firstLineIsNewline = dockerCommand.indexOf("\n");
    if (firstLineIsNewline == 0) {
      dockerCommand = dockerCommand.replaceFirst("\n", "");
    }
    LOGGER.info("dockerCommand = {}", dockerCommand);

    List<String> listEnv = getListEnvs();
    LOGGER.info("docker listEnv = {}", listEnv);

    // check if the interpreter process exit script
    // if interpreter process exit, then container need exit
    StringBuilder sbStartCmd = new StringBuilder();
    sbStartCmd.append("sleep 10; ");
    sbStartCmd.append("process=RemoteInterpreterServer; ");
    sbStartCmd.append("RUNNING_PIDS=$(ps x | grep $process | grep -v grep | awk '{print $1}'); ");
    sbStartCmd.append("while [ ! -z \"$RUNNING_PIDS\" ]; ");
    sbStartCmd.append("do sleep 1; ");
    sbStartCmd.append("RUNNING_PIDS=$(ps x | grep $process | grep -v grep | awk '{print $1}'); ");
    sbStartCmd.append("done");

    // Create container with exposed ports
    final ContainerConfig containerConfig = ContainerConfig.builder()
        .hostConfig(hostConfig)
        .hostname(this.intpEventServerHost)
        .image(containerImage)
        .workingDir("/")
        .env(listEnv)
        .cmd("sh", "-c", sbStartCmd.toString())
        .build();

    try {
      LOGGER.info("wait docker pull image {} ...", containerImage);
      docker.pull(containerImage, new ProgressHandler() {
        @Override
        public void progress(ProgressMessage message) throws DockerException {
          if (null != message.error()) {
            LOGGER.error(message.toString());
          }
        }
      });

      final ContainerCreation containerCreation
          = docker.createContainer(containerConfig, containerName);
      this.containerId = containerCreation.id();

      // Start container
      docker.startContainer(containerId);

      copyRunFileToContainer(containerId);

      execInContainer(containerId, dockerCommand, false);
    } catch (DockerException e) {
      LOGGER.error(e.getMessage(), e);
      throw new IOException(e.getMessage());
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
      throw new IOException(e.getMessage());
    }

    long startTime = System.currentTimeMillis();

    // wait until interpreter send dockerStarted message through thrift rpc
    synchronized (dockerStarted) {
      if (!dockerStarted.get()) {
        try {
          dockerStarted.wait(getConnectTimeout());
        } catch (InterruptedException e) {
          LOGGER.error("Remote interpreter is not accessible");
          throw new IOException(e.getMessage());
        }
      }
    }

    if (!dockerStarted.get()) {
      LOGGER.info("Interpreter docker creation is time out in {} seconds",
          getConnectTimeout() / 1000);
    }

    // waits for interpreter thrift rpc server ready
    while (System.currentTimeMillis() - startTime < getConnectTimeout()) {
      if (RemoteInterpreterUtils.checkIfRemoteEndpointAccessible(getHost(), getPort())) {
        break;
      } else {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
    }
  }

  @Override
  public void processStarted(int port, String host) {
    containerHost = host;
    containerPort = port;
    LOGGER.info("Interpreter container created {}:{}", containerHost, containerPort);
    synchronized (dockerStarted) {
      dockerStarted.set(true);
      dockerStarted.notify();
    }
  }

  @VisibleForTesting
  Properties getTemplateBindings() throws IOException {
    Properties dockerProperties = new Properties();

    // docker template properties
    dockerProperties.put("CONTAINER_ZEPPELIN_HOME", zeppelinHome);
    dockerProperties.put("zeppelin.interpreter.container.image", containerImage);
    dockerProperties.put("zeppelin.interpreter.group.id", interpreterGroupId);
    dockerProperties.put("zeppelin.interpreter.group.name", interpreterGroupName);
    dockerProperties.put("zeppelin.interpreter.setting.name", interpreterSettingName);
    dockerProperties.put("zeppelin.interpreter.localRepo", "/tmp/local-repo");
    dockerProperties.put("zeppelin.interpreter.rpc.portRange",
        dockerIntpServicePort + ":" + dockerIntpServicePort);
    dockerProperties.put("zeppelin.server.rpc.host", intpEventServerHost);
    dockerProperties.put("zeppelin.server.rpc.portRange", intpEventServerPort);

    // interpreter properties overrides the values
    dockerProperties.putAll(Maps.fromProperties(properties));

    return dockerProperties;
  }

  @VisibleForTesting
  List<String> getListEnvs() throws SocketException, UnknownHostException {
    // environment variables
    envs.put("ZEPPELIN_HOME", zeppelinHome);
    envs.put("ZEPPELIN_CONF_DIR", zeppelinHome + "/conf");
    envs.put("ZEPPELIN_FORCE_STOP", "true");
    envs.put("SPARK_HOME", this.CONTAINER_SPARK_HOME);

    // set container time zone
    String dockerTimeZone = System.getenv("DOCKER_TIME_ZONE");
    if (StringUtils.isBlank(dockerTimeZone)) {
      dockerTimeZone = TimeZone.getDefault().getID();
    }
    envs.put("TZ", dockerTimeZone);

    List<String> listEnv = new ArrayList<>();
    for (Map.Entry<String, String> entry : this.envs.entrySet()) {
      String env = entry.getKey() + "=" + entry.getValue();
      listEnv.add(env);
    }
    return listEnv;
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
    }
    try {
      // Kill container
      docker.killContainer(containerName);

      // Remove container
      docker.removeContainer(containerName);
    } catch (DockerException | InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    }

    // Close the docker client
    docker.close();
  }

  // Because docker can't create a container with the same name, it will cause the creation to fail.
  // If the zeppelin service is abnormal and the container that was created is not closed properly,
  // the container will not be created again.
  private void removeExistContainer(String containerName) {
    boolean isExist = false;
    try {
      final List<Container> containers
          = docker.listContainers(DockerClient.ListContainersParam.allContainers());
      for (Container container : containers) {
        for (String name : container.names()) {
          // because container name like '/md-shared', so need add '/'
          if (StringUtils.equals(name, "/" + containerName)) {
            isExist = true;
            break;
          }
        }
      }

      if (isExist == true) {
        LOGGER.info("kill exist container {}", containerName);
        docker.killContainer(containerName);
      }
    } catch (DockerException | InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      try {
        if (isExist == true) {
          docker.removeContainer(containerName);
        }
      } catch (DockerException | InterruptedException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }

  @Override
  public String getHost() {
    return containerHost;
  }

  @Override
  public int getPort() {
    return containerPort;
  }

  @Override
  public boolean isRunning() {
    if (RemoteInterpreterUtils.checkIfRemoteEndpointAccessible(getHost(), getPort())) {
      return true;
    }
    return false;
  }

  @Override
  public String getErrorMessage() {
    return null;
  }

  // upload configure file to submarine interpreter container
  // keytab file & zeppelin-site.xml & krb5.conf
  // The submarine configures the mount file into the container through `localization`
  // NOTE: The path to the file uploaded to the container,
  // Can not be repeated, otherwise it will lead to failure.
  private void copyRunFileToContainer(String containerId)
      throws IOException, DockerException, InterruptedException {
    HashMap<String, String> copyFiles = new HashMap<>();

    // Rebuild directory
    rmInContainer(containerId, zeppelinHome);
    mkdirInContainer(containerId, zeppelinHome);


    // 1) zeppelin-site.xml is uploaded to `${CONTAINER_ZEPPELIN_HOME}` directory in the container
    String confPath = "/conf";
    String zeplConfPath = getPathByHome(zeppelinHome, confPath);
    mkdirInContainer(containerId, zeplConfPath);
    copyFiles.put(zeplConfPath + "/zeppelin-site.xml", zeplConfPath + "/zeppelin-site.xml");
    copyFiles.put(zeplConfPath + "/log4j.properties", zeplConfPath + "/log4j.properties");
    copyFiles.put(zeplConfPath + "/log4j_yarn_cluster.properties",
        zeplConfPath + "/log4j_yarn_cluster.properties");

    // 2) upload krb5.conf to container
    String krb5conf = "/etc/krb5.conf";
    File krb5File = new File(krb5conf);
    if (krb5File.exists()) {
      rmInContainer(containerId, krb5conf);
      copyFiles.put(krb5conf, krb5conf);
    } else {
      LOGGER.warn("{} file not found, Did not upload the krb5.conf to the container!", krb5conf);
    }

    // TODO(Vince): Interpreter specific settings, we should consider general property or some
    // other more elegant solution
    // 3) Get the keytab file in each interpreter properties
    // Upload Keytab file to container, Keep the same directory as local host
    // 3.1) shell interpreter properties keytab file
    String intpKeytab = properties.getProperty("zeppelin.shell.keytab.location", "");
    if (StringUtils.isBlank(intpKeytab)) {
      // 3.2) spark interpreter properties keytab file
      intpKeytab = properties.getProperty("spark.yarn.keytab", "");
    }
    if (StringUtils.isBlank(intpKeytab)) {
      // 3.3) submarine interpreter properties keytab file
      intpKeytab = properties.getProperty("submarine.hadoop.keytab", "");
    }
    if (StringUtils.isBlank(intpKeytab)) {
      // 3.4) livy interpreter properties keytab file
      intpKeytab = properties.getProperty("zeppelin.livy.keytab", "");
    }
    if (StringUtils.isBlank(intpKeytab)) {
      // 3.5) jdbc interpreter properties keytab file
      intpKeytab = properties.getProperty("zeppelin.jdbc.keytab.location", "");
    }
    if (!StringUtils.isBlank(intpKeytab) && !copyFiles.containsKey(intpKeytab)) {
      LOGGER.info("intpKeytab : {}", intpKeytab);
      copyFiles.put(intpKeytab, intpKeytab);
    }
    // 3.6) zeppelin server keytab file
    String zeppelinServerKeytab = zconf.getString(ZEPPELIN_SERVER_KERBEROS_KEYTAB);
    if (!StringUtils.isBlank(zeppelinServerKeytab)
        && !copyFiles.containsKey(zeppelinServerKeytab)) {
      copyFiles.put(zeppelinServerKeytab, zeppelinServerKeytab);
    }

    // 4) hadoop conf dir
    if (envs.containsKey("HADOOP_CONF_DIR")) {
      String hadoopConfDir = envs.get("HADOOP_CONF_DIR");
      copyFiles.put(hadoopConfDir, hadoopConfDir);
    }

    // 5) spark conf dir
    if (envs.containsKey("SPARK_CONF_DIR")) {
      String sparkConfDir = envs.get("SPARK_CONF_DIR");
      rmInContainer(containerId, CONTAINER_SPARK_HOME + "/conf");
      mkdirInContainer(containerId, CONTAINER_SPARK_HOME + "/conf");
      copyFiles.put(sparkConfDir, CONTAINER_SPARK_HOME + "/conf");
      envs.put("SPARK_CONF_DIR", CONTAINER_SPARK_HOME + "/conf");
    }

    if (uploadLocalLibToContainter){
      // 6) ${ZEPPELIN_HOME}/bin is uploaded to `${CONTAINER_ZEPPELIN_HOME}`
      //    directory in the container
      String binPath = "/bin";
      String zeplBinPath = getPathByHome(zeppelinHome, binPath);
      mkdirInContainer(containerId, zeplBinPath);
      docker.copyToContainer(new File(zeplBinPath).toPath(), containerId, zeplBinPath);

      // 7) ${ZEPPELIN_HOME}/interpreter/spark is uploaded to `${CONTAINER_ZEPPELIN_HOME}`
      //    directory in the container
      String intpGrpPath = "/interpreter/" + interpreterGroupName;
      String intpGrpAllPath = getPathByHome(zeppelinHome, intpGrpPath);
      mkdirInContainer(containerId, intpGrpAllPath);
      docker.copyToContainer(new File(intpGrpAllPath).toPath(), containerId, intpGrpAllPath);

      // 8) ${ZEPPELIN_HOME}/lib/interpreter/zeppelin-interpreter-shaded-<version>.jar
      //    is uploaded to `${CONTAINER_ZEPPELIN_HOME}` directory in the container
      String intpPath = "/interpreter";
      String intpAllPath = getPathByHome(zeppelinHome, intpPath);
      Collection<File> listFiles = FileUtils.listFiles(new File(intpAllPath),
          FileFilterUtils.suffixFileFilter("jar"), null);
      for (File jarfile : listFiles) {
        String jarfilePath = jarfile.getAbsolutePath();
        if (!StringUtils.isBlank(jarfilePath)
            && !copyFiles.containsKey(jarfilePath)) {
          copyFiles.put(jarfilePath, jarfilePath);
        }
      }
    }

    deployToContainer(containerId, copyFiles);
  }

  private void deployToContainer(String containerId, HashMap<String, String> copyFiles)
      throws InterruptedException, DockerException, IOException {
    // mkdir CONTAINER_UPLOAD_TAR_DIR
    mkdirInContainer(containerId, CONTAINER_UPLOAD_TAR_DIR);

    // file tar package
    String tarFile = file2Tar(copyFiles);

    // copy tar to ZEPPELIN_CONTAINER_DIR, auto unzip
    InputStream inputStream = new FileInputStream(tarFile);
    try {
      docker.copyToContainer(inputStream, containerId, CONTAINER_UPLOAD_TAR_DIR);
    } finally {
      inputStream.close();
    }

    // copy all files in CONTAINER_UPLOAD_TAR_DIR to the root directory
    cpdirInContainer(containerId, CONTAINER_UPLOAD_TAR_DIR + "/*", "/");

    // delete tar file in the local
    File fileTar = new File(tarFile);
    fileTar.delete();
  }

  private void mkdirInContainer(String containerId, String path)
      throws DockerException, InterruptedException {
    String execCommand = "mkdir " + path + " -p";
    execInContainer(containerId, execCommand, true);
  }

  private void rmInContainer(String containerId, String path)
      throws DockerException, InterruptedException {
    String execCommand = "rm " + path + " -R";
    execInContainer(containerId, execCommand, true);
  }

  private void cpdirInContainer(String containerId, String from, String to)
      throws DockerException, InterruptedException {
    String execCommand = "cp " + from + " " + to + " -R";
    execInContainer(containerId, execCommand, true);
  }

  private void execInContainer(String containerId, String execCommand, boolean logout)
      throws DockerException, InterruptedException {

    LOGGER.info("exec container commmand: " + execCommand);

    final String[] command = {"sh", "-c", execCommand};
    final ExecCreation execCreation = docker.execCreate(
        containerId, command, DockerClient.ExecCreateParam.attachStdout(),
        DockerClient.ExecCreateParam.attachStderr());

    LogStream logStream = docker.execStart(execCreation.id());
    while (logStream.hasNext() && logout) {
      final String log = UTF_8.decode(logStream.next().content()).toString();
      LOGGER.info(log);
    }
  }

  private String file2Tar(HashMap<String, String> copyFiles) throws IOException {
    File tmpDir = Files.createTempDir();

    Date date = new Date();
    String tarFileName = tmpDir.getPath() + date.getTime() + ".tar";

    List<TarFileEntry> tarFileEntries = new ArrayList<>();
    for (Map.Entry<String, String> entry : copyFiles.entrySet()) {
      String filePath = entry.getKey();
      String archivePath = entry.getValue();
      TarFileEntry tarFileEntry = new TarFileEntry(new File(filePath), archivePath);
      tarFileEntries.add(tarFileEntry);
    }

    TarUtils.compress(tarFileName, tarFileEntries);

    return tarFileName;
  }

  @VisibleForTesting
  boolean isSpark() {
    return "spark".equalsIgnoreCase(interpreterGroupName);
  }

  private String getZeppelinHome() throws IOException {
    String zeppelinHome = zconf.getZeppelinHome();
    if (System.getenv("ZEPPELIN_HOME") != null) {
      zeppelinHome = System.getenv("ZEPPELIN_HOME");
    }

    // check zeppelinHome is exist
    File fileZeppelinHome = new File(zeppelinHome);
    if (fileZeppelinHome.exists() && fileZeppelinHome.isDirectory()) {
      return zeppelinHome;
    }

    throw new IOException("Can't find zeppelin home path!");
  }

  // ${ZEPPELIN_HOME}/interpreter/${interpreter-name}
  // ${ZEPPELIN_HOME}/lib/interpreter
  private String getPathByHome(String homeDir, String path) throws IOException {
    File file = null;
    if (null == homeDir || StringUtils.isEmpty(homeDir)) {
      file = new File(path);
    } else {
      file = new File(homeDir, path);
    }
    if (file.exists()) {
      return file.getAbsolutePath();
    }

    throw new IOException("Can't find directory in " + homeDir + path + "!");
  }
}
