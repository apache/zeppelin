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
package org.apache.zeppelin.helium;

import static org.apache.zeppelin.helium.HeliumBundleFactory.HELIUM_LOCAL_REPO;
import static org.apache.zeppelin.helium.HeliumPackage.newHeliumPackage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.eirslett.maven.plugins.frontend.lib.InstallationException;
import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeliumBundleFactoryTest {
  private HeliumBundleFactory hbf;
  private File nodeInstallationDir;

  @BeforeEach
  public void setUp() throws InstallationException, TaskRunnerException, IOException {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(),
        new File("../").getAbsolutePath().toString());
    nodeInstallationDir =
        new File(zConf.getAbsoluteDir(ConfVars.ZEPPELIN_DEP_LOCALREPO), HELIUM_LOCAL_REPO);

    hbf = new HeliumBundleFactory(zConf);
    hbf.installNodeAndNpm();
    hbf.copyFrameworkModulesToInstallPath(true);
  }



  @Test
  void testInstallNpm() throws InstallationException {
    assertTrue(new File(nodeInstallationDir, "/node/npm").isFile());
    assertTrue(new File(nodeInstallationDir, "/node/node").isFile());
    assertTrue(new File(nodeInstallationDir, "/node/yarn/dist/bin/yarn").isFile());
  }

  @Test
  void downloadPackage() throws TaskRunnerException {
    HeliumPackage pkg =
        newHeliumPackage(
            HeliumType.VISUALIZATION,
            "lodash",
            "lodash",
            "lodash@3.9.3",
            "",
            null,
            "license",
            "icon");
    hbf.install(pkg);
    System.out.println(new File(nodeInstallationDir, "/node_modules/lodash"));
    assertTrue(new File(nodeInstallationDir, "/node_modules/lodash").isDirectory());
  }

  @Test
  void bundlePackage() throws IOException, TaskRunnerException {
    HeliumPackage pkg =
        newHeliumPackage(
            HeliumType.VISUALIZATION,
            "zeppelin-bubblechart",
            "zeppelin-bubblechart",
            "zeppelin-bubblechart@0.0.3",
            "",
            null,
            "license",
            "icon");
    File bundle = hbf.buildPackage(pkg, true, true);
    assertTrue(bundle.isFile());
    long lastModified = bundle.lastModified();

    // buildBundle again and check if it served from cache
    bundle = hbf.buildPackage(pkg, false, true);
    assertEquals(lastModified, bundle.lastModified());
  }

  @Test
  void bundleLocalPackage() throws IOException, TaskRunnerException {
    URL res = Resources.getResource("helium/webpack.config.js");
    String resDir = new File(res.getFile()).getParent();
    String localPkg = resDir + "/../../../src/test/resources/helium/vis1";

    HeliumPackage pkg =
        newHeliumPackage(
            HeliumType.VISUALIZATION,
            "vis1",
            "vis1",
            localPkg,
            "",
            null,
            "license",
            "fa fa-coffee");
    File bundle = hbf.buildPackage(pkg, true, true);
    assertTrue(bundle.isFile());
  }

  // TODO(zjffdu) Ignore flaky test, enable it later after fixing this flaky test
  // @Test
  public void bundleErrorPropagation() throws IOException, TaskRunnerException {
    URL res = Resources.getResource("helium/webpack.config.js");
    String resDir = new File(res.getFile()).getParent();
    String localPkg = resDir + "/../../../src/test/resources/helium/vis2";

    HeliumPackage pkg =
        newHeliumPackage(
            HeliumType.VISUALIZATION,
            "vis2",
            "vis2",
            localPkg,
            "",
            null,
            "license",
            "fa fa-coffee");
    File bundle = null;
    try {
      bundle = hbf.buildPackage(pkg, true, true);
      // should throw exception
      assertTrue(false);
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("error in the package"));
    }
    assertNull(bundle);
  }

  @Test
  void switchVersion() throws IOException, TaskRunnerException {
    URL res = Resources.getResource("helium/webpack.config.js");
    String resDir = new File(res.getFile()).getParent();

    HeliumPackage pkgV1 =
        newHeliumPackage(
            HeliumType.VISUALIZATION,
            "zeppelin-bubblechart",
            "zeppelin-bubblechart",
            "zeppelin-bubblechart@0.0.3",
            "",
            null,
            "license",
            "icon");

    HeliumPackage pkgV2 =
        newHeliumPackage(
            HeliumType.VISUALIZATION,
            "zeppelin-bubblechart",
            "zeppelin-bubblechart",
            "zeppelin-bubblechart@0.0.1",
            "",
            null,
            "license",
            "icon");
    List<HeliumPackage> pkgsV1 = new LinkedList<>();
    pkgsV1.add(pkgV1);

    List<HeliumPackage> pkgsV2 = new LinkedList<>();
    pkgsV2.add(pkgV2);

    File bundle1 = hbf.buildPackage(pkgV1, true, true);
    File bundle2 = hbf.buildPackage(pkgV2, true, true);

    assertNotSame(bundle1.lastModified(), bundle2.lastModified());
  }
}
