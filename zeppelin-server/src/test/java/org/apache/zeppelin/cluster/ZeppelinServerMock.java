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
package org.apache.zeppelin.cluster;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.plugin.PluginManager;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.utils.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ZeppelinServerMock {
  protected static final Logger LOG = LoggerFactory.getLogger(ZeppelinServerMock.class);

  protected static final boolean WAS_RUNNING = AbstractTestRestApi.checkIfServerIsRunning();

  protected static File zeppelinHome;
  protected static File confDir;
  protected static File notebookDir;

  private String getUrl(String path) {
    String url;
    if (System.getProperty("url") != null) {
      url = System.getProperty("url");
    } else {
      url = "http://localhost:8080";
    }
    url += AbstractTestRestApi.REST_API_URL;
    if (path != null) {
      url += path;
    }

    return url;
  }

  static ExecutorService executor;
  protected static final Runnable SERVER = new Runnable() {
    @Override
    public void run() {
      try {
        TestUtils.clearInstances();
        ZeppelinServer.main(new String[]{""});
      } catch (Exception e) {
        LOG.error("Exception in WebDriverManager while getWebDriver ", e);
        throw new RuntimeException(e);
      }
    }
  };

  private static void start(String testClassName, boolean cleanData, ZeppelinConfiguration zconf)
      throws Exception {
    LOG.info("Starting ZeppelinServer testClassName: {}", testClassName);

    if (!WAS_RUNNING) {
      // copy the resources files to a temp folder
      zeppelinHome = new File("..");
      LOG.info("ZEPPELIN_HOME: " + zeppelinHome.getAbsolutePath());
      confDir = new File(zeppelinHome, "conf_" + testClassName);
      confDir.mkdirs();
      zconf.save(confDir + "/zeppelin-site.xml");

      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(),
          zeppelinHome.getAbsolutePath());
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_WAR.getVarName(),
          new File("../zeppelin-web/dist").getAbsolutePath());
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName(),
          confDir.getAbsolutePath());
      System.setProperty(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT.getVarName(),
          "spark");
      notebookDir = new File(zeppelinHome.getAbsolutePath() + "/notebook_" + testClassName);
      if (cleanData) {
        FileUtils.deleteDirectory(notebookDir);
      }
      System.setProperty(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
          notebookDir.getPath()
      );
      LOG.info("zconf.getClusterAddress() = {}", zconf.getClusterAddress());

      // some test profile does not build zeppelin-web.
      // to prevent zeppelin starting up fail, create zeppelin-web/dist directory
      new File("../zeppelin-web/dist").mkdirs();

      LOG.info("Staring ZeppelinServerMock Zeppelin up...");

      executor = Executors.newSingleThreadExecutor();
      executor.submit(SERVER);
      long s = System.currentTimeMillis();
      boolean started = false;
      while (System.currentTimeMillis() - s < 1000 * 60 * 3) {  // 3 minutes
        Thread.sleep(2000);
        started = AbstractTestRestApi.checkIfServerIsRunning();
        if (started == true) {
          break;
        }
      }
      if (started == false) {
        throw new RuntimeException("Can not start Zeppelin server");
      }
      //ZeppelinServer.notebook.setParagraphJobListener(NotebookServer.getInstance());
      LOG.info("ZeppelinServerMock stared.");
    }
  }

  protected static void startUp(String testClassName, ZeppelinConfiguration zconf) throws Exception {
    start(testClassName, true, zconf);
  }

  protected static void shutDown() throws Exception {
    shutDown(true);
  }

  protected static void shutDown(final boolean deleteConfDir) throws Exception {
    if (!WAS_RUNNING && TestUtils.getInstance(Notebook.class) != null) {
      // restart interpreter to stop all interpreter processes
      List<InterpreterSetting> settingList = TestUtils.getInstance(Notebook.class).getInterpreterSettingManager()
          .get();
      if (!TestUtils.getInstance(Notebook.class).getConf().isRecoveryEnabled()) {
        for (InterpreterSetting setting : settingList) {
          TestUtils.getInstance(Notebook.class).getInterpreterSettingManager().restart(setting.getId());
        }
      }
      LOG.info("ZeppelinServerMock shutDown...");
      executor.shutdown();
      executor.shutdownNow();
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName());
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_WAR.getVarName());
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName());
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONFIG_FS_DIR.getVarName());
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT.getVarName());
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName());

      long s = System.currentTimeMillis();
      boolean started = true;
      while (System.currentTimeMillis() - s < 1000 * 60 * 3) {  // 3 minutes
        Thread.sleep(2000);
        started = AbstractTestRestApi.checkIfServerIsRunning();
        if (started == false) {
          break;
        }
      }
      if (started == true) {
        throw new RuntimeException("Can not stop Zeppelin server");
      }

      ClusterManagerServer clusterManagerServer =
              ClusterManagerServer.getInstance(ZeppelinConfiguration.create());
      clusterManagerServer.shutdown();

      LOG.info("ZeppelinServerMock terminated.");

      if (deleteConfDir) {
        FileUtils.deleteDirectory(confDir);
      }
      PluginManager.reset();
      ZeppelinConfiguration.reset();
    }
  }


}
