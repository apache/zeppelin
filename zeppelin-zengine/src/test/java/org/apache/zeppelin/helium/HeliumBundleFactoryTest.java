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

import com.github.eirslett.maven.plugins.frontend.lib.InstallationException;
import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;
import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.apache.zeppelin.conf.ZeppelinConfiguration;

import static org.junit.Assert.*;

public class HeliumBundleFactoryTest {
  private File tmpDir;
  private ZeppelinConfiguration conf;
  private HeliumBundleFactory hbf;
  private static File nodeInstallationDir = new File(
      System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_nodeCache");

  @BeforeClass
  static public void beforeAll() throws IOException {
    FileUtils.deleteDirectory(nodeInstallationDir);
  }

  @Before
  public void setUp() throws InstallationException, TaskRunnerException, IOException {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    tmpDir.mkdirs();

    // get module dir
    URL res = Resources.getResource("helium/webpack.config.js");
    String resDir = new File(res.getFile()).getParent();
    File moduleDir = new File(resDir + "/../../../../zeppelin-web/src/app/");

    conf = new ZeppelinConfiguration();

    hbf = new HeliumBundleFactory(conf,
        nodeInstallationDir,
        tmpDir,
        new File(moduleDir, "tabledata"),
        new File(moduleDir, "visualization"),
        new File(moduleDir, "spell"));
    hbf.installNodeAndNpm();
    hbf.copyFrameworkModulesToInstallPath(true);
  }

  @After
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(tmpDir);
  }

  @Test
  public void testInstallNpm() throws InstallationException {
    assertTrue(new File(nodeInstallationDir, "/node/npm").isFile());
    assertTrue(new File(nodeInstallationDir, "/node/node").isFile());
    assertTrue(new File(nodeInstallationDir, "/node/yarn/dist/bin/yarn").isFile());
  }

  @Test
  public void downloadPackage() throws TaskRunnerException {
    HeliumPackage pkg = new HeliumPackage(
        HeliumType.VISUALIZATION,
        "lodash",
        "lodash",
        "lodash@3.9.3",
        "",
        null,
        "license",
        "icon"
    );
    hbf.install(pkg);
    assertTrue(new File(tmpDir,
        HeliumBundleFactory.HELIUM_LOCAL_REPO + "/node_modules/lodash").isDirectory());
  }

  @Test
  public void bundlePackage() throws IOException, TaskRunnerException {
    HeliumPackage pkg = new HeliumPackage(
        HeliumType.VISUALIZATION,
        "zeppelin-bubblechart",
        "zeppelin-bubblechart",
        "zeppelin-bubblechart@0.0.3",
        "",
        null,
        "license",
        "icon"
    );
    File bundle = hbf.buildPackage(pkg, true, true);
    assertTrue(bundle.isFile());
    long lastModified = bundle.lastModified();

    // buildBundle again and check if it served from cache
    bundle = hbf.buildPackage(pkg, false, true);
    assertEquals(lastModified, bundle.lastModified());
  }

  @Test
  public void bundleLocalPackage() throws IOException, TaskRunnerException {
    URL res = Resources.getResource("helium/webpack.config.js");
    String resDir = new File(res.getFile()).getParent();
    String localPkg = resDir + "/../../../src/test/resources/helium/vis1";

    HeliumPackage pkg = new HeliumPackage(
        HeliumType.VISUALIZATION,
        "vis1",
        "vis1",
        localPkg,
        "",
        null,
        "license",
        "fa fa-coffee"
    );
    File bundle = hbf.buildPackage(pkg, true, true);
    assertTrue(bundle.isFile());
  }

  @Test
  public void bundleErrorPropagation() throws IOException, TaskRunnerException {
    URL res = Resources.getResource("helium/webpack.config.js");
    String resDir = new File(res.getFile()).getParent();
    String localPkg = resDir + "/../../../src/test/resources/helium/vis2";

    HeliumPackage pkg = new HeliumPackage(
        HeliumType.VISUALIZATION,
        "vis2",
        "vis2",
        localPkg,
        "",
        null,
        "license",
        "fa fa-coffee"
    );
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
  public void switchVersion() throws IOException, TaskRunnerException {
    URL res = Resources.getResource("helium/webpack.config.js");
    String resDir = new File(res.getFile()).getParent();

    HeliumPackage pkgV1 = new HeliumPackage(
        HeliumType.VISUALIZATION,
        "zeppelin-bubblechart",
        "zeppelin-bubblechart",
        "zeppelin-bubblechart@0.0.3",
        "",
        null,
        "license",
        "icon"
    );

    HeliumPackage pkgV2 = new HeliumPackage(
        HeliumType.VISUALIZATION,
        "zeppelin-bubblechart",
        "zeppelin-bubblechart",
        "zeppelin-bubblechart@0.0.1",
        "",
        null,
        "license",
        "icon"
    );
    List<HeliumPackage> pkgsV1 = new LinkedList<>();
    pkgsV1.add(pkgV1);

    List<HeliumPackage> pkgsV2 = new LinkedList<>();
    pkgsV2.add(pkgV2);

    File bundle1 = hbf.buildPackage(pkgV1, true, true);
    File bundle2 = hbf.buildPackage(pkgV2, true, true);

    assertNotSame(bundle1.lastModified(), bundle2.lastModified());
  }
}
