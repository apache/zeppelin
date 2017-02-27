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

import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeliumTest {
  private File tmpDir;
  private File localRegistryPath;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    tmpDir.mkdirs();
    localRegistryPath = new File(tmpDir, "helium");
    localRegistryPath.mkdirs();
  }

  @After
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(tmpDir);
  }

  @Test
  public void testSaveLoadConf() throws IOException, URISyntaxException, TaskRunnerException {
    // given
    File heliumConf = new File(tmpDir, "helium.conf");
    Helium helium = new Helium(heliumConf.getAbsolutePath(), localRegistryPath.getAbsolutePath(),
        null, null, null);
    assertFalse(heliumConf.exists());

    // when
    helium.save();

    // then
    assertTrue(heliumConf.exists());

    // then load without exception
    Helium heliumRestored = new Helium(
        heliumConf.getAbsolutePath(), localRegistryPath.getAbsolutePath(), null, null, null);
  }

  @Test
  public void testRestoreRegistryInstances() throws IOException, URISyntaxException, TaskRunnerException {
    File heliumConf = new File(tmpDir, "helium.conf");
    Helium helium = new Helium(
        heliumConf.getAbsolutePath(), localRegistryPath.getAbsolutePath(), null, null, null);
    HeliumTestRegistry registry1 = new HeliumTestRegistry("r1", "r1");
    HeliumTestRegistry registry2 = new HeliumTestRegistry("r2", "r2");
    helium.addRegistry(registry1);
    helium.addRegistry(registry2);

    // when
    registry1.add(new HeliumPackage(
        HeliumType.APPLICATION,
        "name1",
        "desc1",
        "artifact1",
        "className1",
        new String[][]{},
        "",
        ""));

    registry2.add(new HeliumPackage(
        HeliumType.APPLICATION,
        "name2",
        "desc2",
        "artifact2",
        "className2",
        new String[][]{},
        "",
        ""));

    // then
    assertEquals(2, helium.getAllPackageInfo().size());
  }


  @Test
  public void testRefresh() throws IOException, URISyntaxException, TaskRunnerException {
    File heliumConf = new File(tmpDir, "helium.conf");
    Helium helium = new Helium(
        heliumConf.getAbsolutePath(), localRegistryPath.getAbsolutePath(), null, null, null);
    HeliumTestRegistry registry1 = new HeliumTestRegistry("r1", "r1");
    helium.addRegistry(registry1);

    // when
    registry1.add(new HeliumPackage(
        HeliumType.APPLICATION,
        "name1",
        "desc1",
        "artifact1",
        "className1",
        new String[][]{},
        "",
        ""));

    // then
    assertEquals(1, helium.getAllPackageInfoWithoutRefresh().size());

    // when
    registry1.add(new HeliumPackage(
        HeliumType.APPLICATION,
        "name2",
        "desc2",
        "artifact2",
        "className2",
        new String[][]{},
        "",
        ""));

    // then
    assertEquals(1, helium.getAllPackageInfoWithoutRefresh().size());
    assertEquals(2, helium.getAllPackageInfo(true, null).size());
  }
}
