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
package org.apache.zeppelin.conf;


import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZeppelinConfigurationTest {

  @Test
  void getAllowedOrigins2Test() throws MalformedURLException {

    ZeppelinConfiguration conf = ZeppelinConfiguration.load("test-zeppelin-site2.xml");
    List<String> origins = conf.getAllowedOrigins();
    assertEquals(2, origins.size());
    assertEquals("http://onehost:8080", origins.get(0));
    assertEquals("http://otherhost.com", origins.get(1));
  }

  @Test
  void getAllowedOrigins1Test() throws MalformedURLException {

    ZeppelinConfiguration conf = ZeppelinConfiguration.load("test-zeppelin-site1.xml");
    List<String> origins = conf.getAllowedOrigins();
    assertEquals(1, origins.size());
    assertEquals("http://onehost:8080", origins.get(0));
  }

  @Test
  void getAllowedOriginsNoneTest() throws MalformedURLException {

    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    List<String> origins = conf.getAllowedOrigins();
    assertEquals(1, origins.size());
  }

  @Test
  void isWindowsPathTestTrue() {

    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    Boolean isIt = conf.isWindowsPath("c:\\test\\file.txt");
    assertTrue(isIt);
  }

  @Test
  void isWindowsPathTestFalse() {

    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    Boolean isIt = conf.isWindowsPath("~/test/file.xml");
    assertFalse(isIt);
  }

  @Test
  void isPathWithSchemeTestTrue() {

    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    Boolean isIt = conf.isPathWithScheme("hdfs://hadoop.example.com/zeppelin/notebook");
    assertTrue(isIt);
  }

  @Test
  void isPathWithSchemeTestFalse() {

    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    Boolean isIt = conf.isPathWithScheme("~/test/file.xml");
    assertFalse(isIt);
  }

  @Test
  void isPathWithInvalidSchemeTest() {

    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    Boolean isIt = conf.isPathWithScheme("c:\\test\\file.txt");
    assertFalse(isIt);
  }

  @Test
  void getNotebookDirTest() {
    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    String notebookLocation = conf.getNotebookDir();
    assertTrue(notebookLocation.endsWith("notebook"));
  }

  @Test
  void isNotebookPublicTest() {

    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    boolean isIt = conf.isNotebookPublic();
    assertTrue(isIt);
  }

  @Test
  void getPathTest() {
    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    conf.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), "/usr/lib/zeppelin");
    assertEquals("/usr/lib/zeppelin", conf.getZeppelinHome());
    assertEquals("/usr/lib/zeppelin/conf", conf.getConfDir());
  }

  @Test
  void getConfigFSPath() {
    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    conf.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), "/usr/lib/zeppelin");
    conf.setProperty(ConfVars.ZEPPELIN_CONFIG_FS_DIR.getVarName(), "conf");
    assertEquals("/usr/lib/zeppelin/conf", conf.getConfigFSDir(true));

    conf.setProperty(ConfVars.ZEPPELIN_CONFIG_STORAGE_CLASS.getVarName(),
        "org.apache.zeppelin.storage.FileSystemConfigStorage");
    assertEquals("conf", conf.getConfigFSDir(false));
  }

  @Test
  void checkParseException() {
    ZeppelinConfiguration conf = ZeppelinConfiguration.load("zeppelin-test-site.xml");
    // when
    conf.setProperty(ConfVars.ZEPPELIN_PORT.getVarName(), "luke skywalker");
    // then
    assertEquals(8080, conf.getServerPort());

    // when
    conf.setProperty(ConfVars.ZEPPELIN_PORT.getVarName(), "12345");
    // then
    assertEquals(12345, conf.getServerPort());
  }
}
