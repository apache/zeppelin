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

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class HeliumLocalRegistryTest {
  private File tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    tmpDir.mkdirs();
  }

  @After
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(tmpDir);
  }

  @Test
  public void testGetAllPackage() throws IOException {
    // given
    File r1Path = new File(tmpDir, "r1");
    HeliumLocalRegistry r1 = new HeliumLocalRegistry("r1", r1Path.getAbsolutePath());
    assertEquals(0, r1.getAll().size());

    // when
    Gson gson = new Gson();
    HeliumPackage pkg1 = new HeliumPackage(HeliumType.APPLICATION,
        "app1",
        "desc1",
        "artifact1",
        "classname1",
        new String[][]{},
        "license",
        "");
    FileUtils.writeStringToFile(new File(r1Path, "pkg1.json"), gson.toJson(pkg1));

    // then
    assertEquals(1, r1.getAll().size());
  }
}
