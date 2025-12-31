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
package org.apache.zeppelin;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.server.ZeppelinServer;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MiniZeppelinServer implements AutoCloseable {
  protected static final Logger LOGGER = LoggerFactory.getLogger(MiniZeppelinServer.class);

  private final File zeppelinHome;
  private final File confDir;
  private final File notebookDir;
  private final String classname;
  private ZeppelinServer zepServer;
  private final ZeppelinConfiguration zConf;
  private String serviceLocator;

  private ExecutorService executor;
  protected final Runnable SERVER = new Runnable() {
    @Override
    public void run() {
      try {
        zepServer.startZeppelin();
      } catch (Exception e) {
        LOGGER.error("Exception in MiniZeppelinServer", e);
        throw new RuntimeException(e);
      }
    }
  };

  public MiniZeppelinServer(String classname) throws IOException {
    this(classname, null);
  }

  public MiniZeppelinServer(String classname, String zeppelinConfiguration) throws IOException {
    this.classname = classname;
    zeppelinHome = Files.createTempDirectory(classname).toFile();
    LOGGER.info("ZEPPELIN_HOME: " + zeppelinHome.getAbsolutePath());
    confDir = new File(zeppelinHome, "conf");
    confDir.mkdirs();
    LOGGER.info("ZEPPELIN_CONF_DIR: " + confDir.getAbsolutePath());
    notebookDir = new File(zeppelinHome, "notebook");
    notebookDir.mkdirs();

    zConf = ZeppelinConfiguration.load(zeppelinConfiguration);
    zConf.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(),
        zeppelinHome.getAbsoluteFile().toString());
    Optional<File> webWar = getWebWar();
    Optional<File> webAngularWar = getWebAngularWar();
    if (webWar.isPresent()) {
      zConf.setProperty(ConfVars.ZEPPELIN_WAR.getVarName(), webWar.get().getAbsolutePath());
    } else {
      // some test profile does not build zeppelin-web.
      // to prevent zeppelin starting up fail, create zeppelin-web/dist directory
      File dummyWebDir = new File(zeppelinHome, "zeppelin-web" + File.separator + "dist");
      dummyWebDir.mkdirs();
      zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_WAR.getVarName(),
          dummyWebDir.getAbsolutePath());
    }
    if (webAngularWar.isPresent()) {
      zConf.setProperty(ConfVars.ZEPPELIN_ANGULAR_WAR.getVarName(),
          webAngularWar.get().getAbsolutePath());
    } else {
      File dummyWebDir = new File(zeppelinHome, "zeppelin-web-angular" + File.separator + "dist");
      dummyWebDir.mkdirs();
      zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_ANGULAR_WAR.getVarName(),
          dummyWebDir.getAbsolutePath());
    }
  }

  private Optional<File> getWebWar() {
    File webWarTargetFolder = new File(".." + File.separator + "zeppelin-web" + File.separator + "target");
    if (webWarTargetFolder.exists()) {
      for (File targetFile : webWarTargetFolder.listFiles()) {
        if (targetFile.getName().contains("zeppelin-web")
            && targetFile.getName().contains(".war")) {
          return Optional.of(targetFile);
        }
      }
    }
    return Optional.empty();
  }

  private Optional<File> getWebAngularWar() {
    File webWarTargetFolder =
        new File(".." + File.separator + "zeppelin-web-angular" + File.separator + "target");
    if (webWarTargetFolder.exists()) {
      for (File targetFile : webWarTargetFolder.listFiles()) {
        if (targetFile.getName().contains("zeppelin-web-angular")
            && targetFile.getName().contains(".war")) {
          return Optional.of(targetFile);
        }
      }
    }
    return Optional.empty();
  }

  public void copyBinDir() throws IOException {
    File binDirDest = new File(zeppelinHome, "bin");
    File binDirSrc = new File(".." + File.separator + "bin");
    LOGGER.info("Copy {} to {}", binDirSrc.getAbsolutePath(),
        binDirDest.getAbsolutePath());
    FileUtils.copyDirectory(binDirSrc, binDirDest);
  }

  public File addConfigFile(String file, String data) throws IOException {
    File configfile = new File(confDir, file);
    FileUtils.writeStringToFile(configfile, data, StandardCharsets.UTF_8);
    return configfile;
  }

  public void addInterpreter(String interpreterName) throws IOException {
    copyMainInterpreter();
    File interpreterDirDest = new File(zeppelinHome, "interpreter" + File.separator + interpreterName);
    File interpreterDirSrc =
        new File(".." + File.separator + "interpreter" + File.separator + interpreterName);
    LOGGER.info("Copy {}-Interpreter from {} to {}", interpreterName,
        interpreterDirSrc.getAbsolutePath(),
        interpreterDirDest.getAbsolutePath());
    FileUtils.copyDirectory(interpreterDirSrc, interpreterDirDest);
  }

  public void addLauncher(String launcherName) throws IOException {
    File launcherDirDest = new File(zeppelinHome,
        "plugins" + File.separator + "Launcher" + File.separator + launcherName);
    File launcherDirSrc =
        new File(".." + File.separator + "plugins" + File.separator + "Launcher" + File.separator
            + launcherName);
    LOGGER.info("Copy {} from {} to {}", launcherName, launcherDirSrc.getAbsolutePath(),
        launcherDirDest.getAbsolutePath());
    FileUtils.copyDirectory(launcherDirSrc, launcherDirDest);
  }

  public void copyLogProperties() throws IOException {
    File confDir = new File(".." + File.separator + "conf");
    for (String conffile : confDir.list()) {
      if (conffile.contains("log")) {
        File logPropertiesSrc = new File(confDir, conffile);
        File logPropertiesDest =
            new File(zeppelinHome, "conf" + File.separator + conffile);
        if (!logPropertiesDest.exists()) {
          LOGGER.info("Copy {} to {}", logPropertiesSrc.getAbsolutePath(),
              logPropertiesDest.getAbsolutePath());
          FileUtils.copyFile(logPropertiesSrc, logPropertiesDest);
        }
      }
    }
  }

  public void copyMainInterpreter() throws IOException {
    File interpreterDir = new File(".." + File.separator + "interpreter");
    for (String interpreterfile : interpreterDir.list()) {
      if (interpreterfile.contains("zeppelin-interpreter-shaded")) {
        File zeppelinInterpreterShadedSrc = new File(interpreterDir, interpreterfile);
        File zeppelinInterpreterShadedDest =
            new File(zeppelinHome, "interpreter" + File.separator + interpreterfile);
        if (!zeppelinInterpreterShadedDest.exists()) {
          LOGGER.info("Copy {} to {}", zeppelinInterpreterShadedSrc.getAbsolutePath(),
              zeppelinInterpreterShadedDest.getAbsolutePath());
          FileUtils.copyFile(zeppelinInterpreterShadedSrc, zeppelinInterpreterShadedDest);
        }
      }
    }
  }

  public String getZeppelinHome() {
    return zeppelinHome.getAbsolutePath();
  }

  public ZeppelinConfiguration getZeppelinConfiguration() {
    return zConf;
  }

  public int getFreePort() {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      assertNotNull(serverSocket);
      assertTrue(serverSocket.getLocalPort()> 0);
      return serverSocket.getLocalPort();
    } catch (IOException e) {
        fail("Port is not available");
    }
    return 0;
  }

  public void start() throws Exception {
    start(false);
  }

  public ServiceLocator getServiceLocator() {
    return ServiceLocatorFactory.getInstance().find(serviceLocator);
  }

  public void start(boolean deleteNotebookData) throws Exception {
    this.start(deleteNotebookData,
        ZeppelinServer.DEFAULT_SERVICE_LOCATOR_NAME);
  }

  public void start(boolean deleteNotebookData, String serviceLocator)
      throws Exception {
    LOGGER.info("Starting ZeppelinServer testClassName: {}", classname);
    // copy the resources files to a temp folder
    zConf.setServerPort(getFreePort());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(),
          zeppelinHome.getAbsolutePath());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_SEARCH_INDEX_PATH.getVarName(),
        new File(zeppelinHome, "index").getAbsolutePath());

    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName(),
        confDir.getAbsolutePath());
    zConf.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT.getVarName(),
        "spark");
    if (deleteNotebookData) {
      FileUtils.deleteDirectory(notebookDir);
      notebookDir.mkdirs();
    }
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        notebookDir.getPath());
    LOGGER.info("Staring ZeppelinServerMock Zeppelin up...");
    this.serviceLocator = serviceLocator;
    zepServer = new ZeppelinServer(zConf, serviceLocator);
    executor = Executors.newSingleThreadExecutor();
    executor.submit(SERVER);
    await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofMinutes(3))
        .until(checkIfServerIsRunningCallable());
    LOGGER.info("ZeppelinServerMock started.");
  }

  public boolean checkIfServerIsRunning() {
    boolean isRunning = false;
    HttpGet httpGet = new HttpGet("http://localhost:" + zConf.getServerPort() + "/api/version");
    try (CloseableHttpResponse response = AbstractTestRestApi.getHttpClient().execute(httpGet)) {
      isRunning = response.getStatusLine().getStatusCode() == 200;
    } catch (IOException e) {
      // Ignore
    }
    return isRunning;
  }

  private Callable<Boolean> checkIfServerIsRunningCallable() {
    return () -> checkIfServerIsRunning();
  }

  private Callable<Boolean> checkIfServerIsNotRunningCallable() {
    return () -> !checkIfServerIsRunning();
  }

  public void shutDown() throws Exception {
    shutDown(false);
  }

  public void shutDown(final boolean deleteConfDir) throws Exception {
    if (!executor.isShutdown()) {
      LOGGER.info("ZeppelinServerMock shutDown...");
      zepServer.close();
      executor.shutdown();
      executor.shutdownNow();
      await().pollDelay(2, TimeUnit.SECONDS).atMost(3, TimeUnit.MINUTES)
          .until(checkIfServerIsNotRunningCallable());

      LOGGER.info("ZeppelinServerMock terminated.");

      if (deleteConfDir) {
        FileUtils.deleteDirectory(confDir);
      }
    }
  }

  @Override
  public void close() throws Exception {
    shutDown();
    FileUtils.deleteDirectory(zeppelinHome);
  }

  public void destroy() throws Exception {
    close();
  }

  public <T> T getService(Class<T> clazz) {
    return getServiceLocator().getService(clazz);
  }
}
