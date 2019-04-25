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
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.Constants;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterRunner;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterRunningProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_KEYTAB;
import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_PRINCIPAL;
import static org.apache.zeppelin.interpreter.launcher.YarnConstants.DOCKER_HADOOP_CONF_DIR;
import static org.apache.zeppelin.interpreter.launcher.YarnConstants.DOCKER_HADOOP_HOME;
import static org.apache.zeppelin.interpreter.launcher.YarnConstants.DOCKER_JAVA_HOME;
import static org.apache.zeppelin.interpreter.launcher.YarnConstants.HADOOP_YARN_SUBMARINE_JAR;
import static org.apache.zeppelin.interpreter.launcher.YarnConstants.SUBMARINE_HADOOP_KEYTAB;
import static org.apache.zeppelin.interpreter.launcher.YarnConstants.SUBMARINE_HADOOP_PRINCIPAL;

/**
 * Yarn specific launcher.
 */
public class YarnStandardInterpreterLauncher extends StandardInterpreterLauncher {
  private static final Logger LOGGER = LoggerFactory.getLogger(YarnStandardInterpreterLauncher.class);

  private YarnClient yarnClient = null;

  // Zeppelin home path in Docker container
  // Environment variable `SUBMARINE_ZEPPELIN_CONF_DIR_ENV` in /bin/interpreter.sh
  private static final String CONTAINER_ZEPPELIN_HOME = "/zeppelin";

  // Upload local zeppelin library to container, There are several benefits
  // Avoid the difference between the zeppelin version and the local in the container
  // 1). RemoteInterpreterServer::main(String[] args), Different versions of args may be different
  // 2). bin/interpreter.sh Start command args may be different
  // 3). In the debugging phase for easy updating, Upload the local library file to container
  // After the release, Use the zeppelin library file in the container image
  private boolean uploadLocalLibToContainter = true;

  private static boolean isTest = false;

  public YarnStandardInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) {
    super(zConf, recoveryStorage);

    String uploadLocalLib = System.getenv("UPLOAD_LOCAL_LIB_TO_CONTAINTER");
    if (null != uploadLocalLib && StringUtils.equals(uploadLocalLib, "false")) {
      uploadLocalLibToContainter = false;
    }
  }

  @Override
  public InterpreterClient launch(InterpreterLaunchContext context) throws IOException {
    LOGGER.info("YarnStandardInterpreterLauncher: "
        + context.getInterpreterSettingGroup());

    // Because need to modify the properties, make a clone
    this.properties = (Properties) context.getProperties().clone();
    yarnClient = new YarnClient();

    InterpreterOption option = context.getOption();
    InterpreterRunner runner = context.getRunner();
    String groupName = context.getInterpreterSettingGroup();
    String name = context.getInterpreterSettingName();
    int connectTimeout = getConnectTimeout();
    if (connectTimeout < 200000) {
      // Because yarn need to download docker image,
      // So need to increase the timeout setting.
      connectTimeout = 200000;
    }

    if (option.isExistingProcess()) {
      LOGGER.info("Connect an existing remote interpreter process.");
      return new RemoteInterpreterRunningProcess(
          context.getInterpreterSettingName(),
          connectTimeout,
          option.getHost(),
          option.getPort());
    } else {
      // yarn application name match the pattern [a-z][a-z0-9-]*
      String submarineIntpAppName = YarnClient.formatYarnAppName(
          context.getInterpreterGroupId());

      // setting port range of interpreter container
      String intpPort = String.valueOf(Constants.ZEPPELIN_INTERPRETER_DEFAUlT_PORT);
      String intpPortRange = intpPort + ":" + intpPort;

      String intpAppHostIp = "";
      String intpAppHostPort = "";
      String intpAppContainerPort = "";
      boolean findExistIntpContainer = false;

      // The submarine interpreter already exists in the connection yarn
      // Or create a submarine interpreter
      // 1. Query the IP and port of the submarine interpreter process through the yarn client
      Map<String, String> exportPorts = yarnClient.getAppExportPorts(submarineIntpAppName, intpPort);
      if (exportPorts.containsKey(YarnClient.HOST_IP) && exportPorts.containsKey(YarnClient.HOST_PORT)
          && exportPorts.containsKey(YarnClient.CONTAINER_PORT)) {
        intpAppHostIp = (String) exportPorts.get(YarnClient.HOST_IP);
        intpAppHostPort = (String) exportPorts.get(YarnClient.HOST_PORT);
        intpAppContainerPort = (String) exportPorts.get(YarnClient.CONTAINER_PORT);
        if (StringUtils.equals(intpPort, intpAppContainerPort)) {
          findExistIntpContainer = true;
          LOGGER.info("Detection Submarine interpreter Container hostIp:{}, hostPort:{}, containerPort:{}.",
              intpAppHostIp, intpAppHostPort, intpAppContainerPort);
        }
      }

      if (findExistIntpContainer) {
        return new RemoteInterpreterRunningProcess(
            context.getInterpreterSettingName(),
            connectTimeout,
            intpAppHostIp,
            Integer.parseInt(intpAppHostPort));
      } else {
        String localRepoPath = zConf.getInterpreterLocalRepoPath() + "/"
            + context.getInterpreterSettingId();
        return new YarnRemoteInterpreterProcess(
            runner != null ? runner.getPath() : zConf.getInterpreterRemoteRunnerPath(),
            context.getZeppelinServerRPCPort(), context.getZeppelinServerHost(), intpPortRange,
            zConf.getInterpreterDir() + "/" + groupName, localRepoPath,
            buildEnvFromProperties(context, properties), connectTimeout, name,
            context.getInterpreterGroupId(), option.isUserImpersonate(), properties);
      }
    }
  }

  protected Map<String, String> buildEnvFromProperties(InterpreterLaunchContext context, Properties properties)
      throws IOException {
    Map<String, String> env = new HashMap<>();

    // yarn application name match the pattern [a-z][a-z0-9-]*
    String intpYarnAppName = YarnClient.formatYarnAppName(context.getInterpreterGroupId());
    env.put("YARN_APP_NAME", intpYarnAppName);
    env.put("ZEPPELIN_RUN_MODE", "yarn");

    // upload configure file to submarine interpreter container
    // keytab file & zeppelin-site.xml & krb5.conf & hadoop-yarn-submarine-X.X.X-SNAPSHOT.jar
    // The submarine configures the mount file into the container through `localization`
    // NOTE: The path to the file uploaded to the container,
    // Can not be repeated, otherwise it will lead to failure.
    HashMap<String, String> uploaded = new HashMap<>();
    StringBuffer sbLocalization = new StringBuffer();

    // 1) zeppelin-site.xml is uploaded to `${CONTAINER_ZEPPELIN_HOME}` directory in the container
    if (null != zConf.getDocument()) {
      String zconfFile = zConf.getDocument().getDocumentURI();
      if (zconfFile.startsWith("file:")) {
        zconfFile = zconfFile.replace("file:", "");
      }
      if (!StringUtils.isEmpty(zconfFile)) {
        sbLocalization.append("--localization \"");
        sbLocalization.append(zconfFile + ":" + CONTAINER_ZEPPELIN_HOME + "/zeppelin-site.xml:rw\"");
        sbLocalization.append(" ");
      }
    }

    String zeppelinHome = getZeppelinHome();
    if (uploadLocalLibToContainter){
      // 2) ${ZEPPELIN_HOME}/interpreter/submarine is uploaded to `${CONTAINER_ZEPPELIN_HOME}`
      //    directory in the container
      String intpPath = "/interpreter/" + context.getInterpreterSettingName();
      String zeplIntpSubmarinePath = getPathByHome(zeppelinHome, intpPath);
      sbLocalization.append("--localization \"");
      sbLocalization.append(zeplIntpSubmarinePath + ":" + CONTAINER_ZEPPELIN_HOME + intpPath + ":rw\"");
      sbLocalization.append(" ");

      // 3) ${ZEPPELIN_HOME}/lib/interpreter is uploaded to `${CONTAINER_ZEPPELIN_HOME}`
      //    directory in the container
      String libIntpPath = "/lib/interpreter";
      String zeplLibIntpPath = getPathByHome(zeppelinHome, libIntpPath);
      sbLocalization.append("--localization \"");
      sbLocalization.append(zeplLibIntpPath + ":" + CONTAINER_ZEPPELIN_HOME + libIntpPath + ":rw\"");
      sbLocalization.append(" ");
    }

    // 4) ${ZEPPELIN_HOME}/conf/log4j.properties
    String log4jPath = "/conf/log4j.properties";
    String zeplLog4jPath = getPathByHome(zeppelinHome, log4jPath);
    sbLocalization.append("--localization \"");
    sbLocalization.append(zeplLog4jPath + ":" + CONTAINER_ZEPPELIN_HOME + log4jPath + ":rw\"");
    sbLocalization.append(" ");

    // 5) Get the keytab file in each interpreter properties
    // Upload Keytab file to container, Keep the same directory as local host
    // 5.1) shell interpreter properties keytab file
    String intpKeytab = properties.getProperty("zeppelin.shell.keytab.location", "");
    if (StringUtils.isBlank(intpKeytab)) {
      // 5.2) spark interpreter properties keytab file
      intpKeytab = properties.getProperty("spark.yarn.keytab", "");
    }
    if (StringUtils.isBlank(intpKeytab)) {
      // 5.3) submarine interpreter properties keytab file
      intpKeytab = properties.getProperty("submarine.hadoop.keytab", "");
    }
    if (StringUtils.isBlank(intpKeytab)) {
      // 5.4) jdbc interpreter properties keytab file
      intpKeytab = properties.getProperty("zeppelin.jdbc.keytab.location", "");
    }
    if (!StringUtils.isBlank(intpKeytab) && !uploaded.containsKey(intpKeytab)) {
      uploaded.put(intpKeytab, "");
      sbLocalization.append("--localization \"");
      sbLocalization.append(intpKeytab + ":" + intpKeytab + ":rw\"").append(" ");
    }

    String zeppelinServerKeytab = zConf.getString(ZEPPELIN_SERVER_KERBEROS_KEYTAB);
    String zeppelinServerPrincipal = zConf.getString(ZEPPELIN_SERVER_KERBEROS_PRINCIPAL);
    if (!StringUtils.isBlank(zeppelinServerKeytab) && !uploaded.containsKey(zeppelinServerKeytab)) {
      uploaded.put(zeppelinServerKeytab, "");
      sbLocalization.append("--localization \"");
      sbLocalization.append(zeppelinServerKeytab + ":" + zeppelinServerKeytab + ":rw\"").append(" ");
    }

    // 6) hadoop-yarn-submarine-X.X.X-SNAPSHOT.jar file upload container, Keep the same directory as local
    String submarineJar = System.getenv(HADOOP_YARN_SUBMARINE_JAR);
    if (submarineJar != null && !StringUtils.isEmpty(submarineJar)) {
      submarineJar = getPathByHome(null, submarineJar);
      sbLocalization.append("--localization \"");
      sbLocalization.append(submarineJar + ":" + submarineJar + ":rw\"").append(" ");
    }

    if (!isTest) {
      // 7) hadoop conf directory upload container, Keep the same directory as local
      File coreSite = findFileOnClassPath("core-site.xml");
      File hdfsSite = findFileOnClassPath("hdfs-site.xml");
      File yarnSite = findFileOnClassPath("yarn-site.xml");
      if (coreSite == null || hdfsSite == null || yarnSite == null) {
        LOGGER.error("hdfs is being used, however we couldn't locate core-site.xml/"
            + "hdfs-site.xml / yarn-site.xml from classpath, please double check you classpath"
            + "setting and make sure they're included.");
        throw new IOException(
            "Failed to locate core-site.xml / hdfs-site.xml / yarn-site.xml from class path");
      }
      String hadoopConfDir = coreSite.getParent();
      if (!StringUtils.isEmpty(hadoopConfDir)) {
        sbLocalization.append("--localization \"");
        sbLocalization.append(hadoopConfDir + ":" + hadoopConfDir + ":rw\"").append(" ");
        // Set the HADOOP_CONF_DIR environment variable in `interpreter.sh`
        env.put("DOCKER_HADOOP_CONF_DIR", hadoopConfDir);
      }
    }

    env.put("YARN_LOCALIZATION_ENV", sbLocalization.toString());
    LOGGER.info("YARN_LOCALIZATION_ENV = " + sbLocalization.toString());

    env.put(SUBMARINE_HADOOP_KEYTAB, zeppelinServerKeytab);
    env.put(SUBMARINE_HADOOP_PRINCIPAL, zeppelinServerPrincipal);

    // Set the zepplin configuration file path environment variable in `interpreter.sh`
    env.put("ZEPPELIN_CONF_DIR_ENV", "--env ZEPPELIN_CONF_DIR=" + CONTAINER_ZEPPELIN_HOME);

    String intpContainerRes = zConf.getYarnContainerResource(context.getInterpreterSettingName());
    env.put("ZEPPELIN_YARN_CONTAINER_RESOURCE", intpContainerRes);

    String yarnContainerImage = zConf.getYarnContainerImage();
    env.put("ZEPPELIN_YARN_CONTAINER_IMAGE", yarnContainerImage);

    for (Object key : properties.keySet()) {
      if (RemoteInterpreterUtils.isEnvString((String) key)) {
        env.put((String) key, properties.getProperty((String) key));
      }
    }
    env.put("INTERPRETER_GROUP_ID", context.getInterpreterSettingId());

    // Check environment variables
    String dockerJavaHome = System.getenv(DOCKER_JAVA_HOME);
    String dockerHadoopHome = System.getenv(DOCKER_HADOOP_HOME);
    String dockerHadoopConf = env.get(DOCKER_HADOOP_CONF_DIR);
    if (StringUtils.isBlank(dockerJavaHome)) {
      LOGGER.warn("DOCKER_JAVA_HOME environment variables not set!");
    }
    if (StringUtils.isBlank(dockerHadoopHome)) {
      LOGGER.warn("DOCKER_HADOOP_HOME environment variables not set!");
    }
    if (StringUtils.isBlank(dockerHadoopConf)) {
      LOGGER.warn("DOCKER_HADOOP_CONF_DIR environment variables not set!");
    }

    return env;
  }

  private String getZeppelinHome() {
    String zeppelinHome = "";
    if (System.getenv("ZEPPELIN_HOME") != null) {
      zeppelinHome = System.getenv("ZEPPELIN_HOME");
    }
    if (StringUtils.isEmpty(zeppelinHome)) {
      zeppelinHome = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME);
    }
    if (StringUtils.isEmpty(zeppelinHome)) {
      // ${ZEPPELIN_HOME}/plugins/Launcher/YarnStandardInterpreterLauncher
      zeppelinHome = getClassPath(YarnStandardInterpreterLauncher.class);
      zeppelinHome = zeppelinHome.replace("/plugins/Launcher/YarnStandardInterpreterLauncher", "");
    }

    // check zeppelinHome is exist
    File fileZeppelinHome = new File(zeppelinHome);
    if (fileZeppelinHome.exists() && fileZeppelinHome.isDirectory()) {
      return zeppelinHome;
    }

    return "";
  }

  @VisibleForTesting
  public static void setTest(Boolean test) {
    isTest = test;
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

    if (isTest) {
      LOGGER.error("Can't find directory in " + homeDir + path + "`!");
      return "";
    }

    throw new IOException("Can't find directory in " + homeDir + path + "`!");
  }

  private File findFileOnClassPath(final String fileName) {
    final String classpath = System.getProperty("java.class.path");
    final String pathSeparator = System.getProperty("path.separator");
    final StringTokenizer tokenizer = new StringTokenizer(classpath, pathSeparator);

    while (tokenizer.hasMoreTokens()) {
      final String pathElement = tokenizer.nextToken();
      final File directoryOrJar = new File(pathElement);
      final File absoluteDirectoryOrJar = directoryOrJar.getAbsoluteFile();
      if (absoluteDirectoryOrJar.isFile()) {
        final File target = new File(absoluteDirectoryOrJar.getParent(),
            fileName);
        if (target.exists()) {
          return target;
        }
      } else{
        final File target = new File(directoryOrJar, fileName);
        if (target.exists()) {
          return target;
        }
      }
    }

    return null;
  }

  /**
   * -----------------------------------------------------------------------
   * getAppPath needs a class attribute parameter of the Java class used
   * by the current program, which can return the packaged
   * The system directory name where the Java executable (jar, war) is located
   * or the directory where the non-packaged Java program is located
   *
   * @param cls
   * @return The return value is the directory where the Java program
   * where the class is located is running.
   * -------------------------------------------------------------------------
   */
  private String getClassPath(Class cls) {
    // Check if the parameters passed in by the user are empty
    if (cls == null) {
      throw new java.lang.IllegalArgumentException("The parameter cannot be empty!");
    }

    ClassLoader loader = cls.getClassLoader();
    // Get the full name of the class, including the package name
    String clsName = cls.getName() + ".class";
    // Get the package where the incoming parameters are located
    Package pack = cls.getPackage();
    String path = "";
    // If not an anonymous package, convert the package name to a path
    if (pack != null) {
      String packName = pack.getName();
      // Here is a simple decision to determine whether it is a Java base class library,
      // preventing users from passing in the JDK built-in class library.
      if (packName.startsWith("java.") || packName.startsWith("javax.")) {
        throw new java.lang.IllegalArgumentException("Do not transfer system classes!");
      }

      // In the name of the class, remove the part of the package name
      // and get the file name of the class.
      clsName = clsName.substring(packName.length() + 1);
      // Determine whether the package name is a simple package name, and if so,
      // directly convert the package name to a path.
      if (packName.indexOf(".") < 0) {
        path = packName + "/";
      } else {
        // Otherwise, the package name is converted to a path according
        // to the component part of the package name.
        int start = 0, end = 0;
        end = packName.indexOf(".");
        while (end != -1) {
          path = path + packName.substring(start, end) + "/";
          start = end + 1;
          end = packName.indexOf(".", start);
        }
        path = path + packName.substring(start) + "/";
      }
    }
    // Call the classReloader's getResource method, passing in the
    // class file name containing the path information.
    java.net.URL url = loader.getResource(path + clsName);
    // Get path information from the URL object
    String realPath = url.getPath();
    // Remove the protocol name "file:" in the path information.
    int pos = realPath.indexOf("file:");
    if (pos > -1) {
      realPath = realPath.substring(pos + 5);
    }
    // Remove the path information and the part that contains the class file information,
    // and get the path where the class is located.
    pos = realPath.indexOf(path + clsName);
    realPath = realPath.substring(0, pos - 1);
    // If the class file is packaged into a JAR file, etc.,
    // remove the corresponding JAR and other package file names.
    if (realPath.endsWith("!")) {
      realPath = realPath.substring(0, realPath.lastIndexOf("/"));
    }
    try {
      realPath = java.net.URLDecoder.decode(realPath, "utf-8");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return realPath;
  }
}
