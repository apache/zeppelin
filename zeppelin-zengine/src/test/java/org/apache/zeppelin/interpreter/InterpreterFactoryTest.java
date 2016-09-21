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

package org.apache.zeppelin.interpreter;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.NullArgumentException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonatype.aether.RepositoryException;

import static org.junit.Assert.*;

public class InterpreterFactoryTest {

  private InterpreterFactory factory;
  private File tmpDir;
  private ZeppelinConfiguration conf;
  private InterpreterContext context;
  private DependencyResolver depResolver;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
    tmpDir.mkdirs();
    new File(tmpDir, "conf").mkdirs();

    Map<String, InterpreterProperty> propertiesMockInterpreter1 = new HashMap<String, InterpreterProperty>();
    propertiesMockInterpreter1.put("PROPERTY_1", new InterpreterProperty("PROPERTY_1", "", "VALUE_1", "desc"));
    propertiesMockInterpreter1.put("property_2", new InterpreterProperty("", "property_2", "value_2", "desc"));
    MockInterpreter1.register("mock1", "mock1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1", propertiesMockInterpreter1);
    MockInterpreter2.register("mock2", "org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), tmpDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "org.apache.zeppelin.interpreter.mock.MockInterpreter1,org.apache.zeppelin.interpreter.mock.MockInterpreter2");
    conf = new ZeppelinConfiguration();
    depResolver = new DependencyResolver(tmpDir.getAbsolutePath() + "/local-repo");
    factory = new InterpreterFactory(conf, new InterpreterOption(false), null, null, null, depResolver, AuthenticationInfo.ANONYMOUS_AUTHENTICATION_INFO);
    context = new InterpreterContext("note", "id", "title", "text", null, null, null, null, null, null, null);
  }

  @After
  public void tearDown() throws Exception {
    FileUtils.deleteDirectory(tmpDir);
  }

  @Test
  public void testBasic() {
    List<InterpreterSetting> all = factory.get();
    InterpreterSetting mock1Setting = null;
    for (InterpreterSetting setting : all) {
      if (setting.getName().equals("mock1")) {
        mock1Setting = setting;
        break;
      }
    }

//    mock1Setting = factory.createNewSetting("mock11", "mock1", new ArrayList<Dependency>(), new InterpreterOption(false), new Properties());

    InterpreterGroup interpreterGroup = mock1Setting.getInterpreterGroup("sharedProcess");
    factory.createInterpretersForNote(mock1Setting, "sharedProcess", "session");

    // get interpreter
    assertNotNull("get Interpreter", interpreterGroup.get("session").get(0));

    // try to get unavailable interpreter
    assertNull(factory.get("unknown"));

    // restart interpreter
    factory.restart(mock1Setting.getId());
    assertNull(mock1Setting.getInterpreterGroup("sharedProcess").get("session"));
  }

  @Test
  public void testRemoteRepl() throws Exception {
    factory = new InterpreterFactory(conf, new InterpreterOption(true), null, null, null, depResolver, AuthenticationInfo.ANONYMOUS_AUTHENTICATION_INFO);
    List<InterpreterSetting> all = factory.get();
    InterpreterSetting mock1Setting = null;
    for (InterpreterSetting setting : all) {
      if (setting.getName().equals("mock1")) {
        mock1Setting = setting;
        break;
      }
    }
    InterpreterGroup interpreterGroup = mock1Setting.getInterpreterGroup("sharedProcess");
    factory.createInterpretersForNote(mock1Setting, "sharedProcess", "session");
    // get interpreter
    assertNotNull("get Interpreter", interpreterGroup.get("session").get(0));
    assertTrue(interpreterGroup.get("session").get(0) instanceof LazyOpenInterpreter);
    LazyOpenInterpreter lazyInterpreter = (LazyOpenInterpreter)(interpreterGroup.get("session").get(0));
    assertTrue(lazyInterpreter.getInnerInterpreter() instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter = (RemoteInterpreter) lazyInterpreter.getInnerInterpreter();
    assertEquals("VALUE_1", remoteInterpreter.getEnv().get("PROPERTY_1"));
    assertEquals("value_2", remoteInterpreter.getProperty("property_2"));
  }

  @Test
  public void testFactoryDefaultList() throws IOException, RepositoryException {
    // get default settings
    List<String> all = factory.getDefaultInterpreterSettingList();
    assertTrue(factory.getRegisteredInterpreterList().size() >= all.size());
  }

  @Test
  public void testExceptions() throws InterpreterException, IOException, RepositoryException {
    List<String> all = factory.getDefaultInterpreterSettingList();
    // add setting with null option & properties expected nullArgumentException.class
    try {
      factory.add("mock2", new ArrayList<InterpreterInfo>(), new LinkedList<Dependency>(), new InterpreterOption(false), new Properties(), "");
    } catch(NullArgumentException e) {
      assertEquals("Test null option" , e.getMessage(),new NullArgumentException("option").getMessage());
    }
    try {
      factory.add("mock2", new ArrayList<InterpreterInfo>(), new LinkedList<Dependency>(), new InterpreterOption(false), new Properties(), "");
    } catch (NullArgumentException e){
      assertEquals("Test null properties" , e.getMessage(),new NullArgumentException("properties").getMessage());
    }
  }


  @Test
  public void testSaveLoad() throws IOException, RepositoryException {
    // interpreter settings
    int numInterpreters = factory.get().size();

    // check if file saved
    assertTrue(new File(conf.getInterpreterSettingPath(AuthenticationInfo.ANONYMOUS_AUTHENTICATION_INFO)).exists());

    factory.createNewSetting("new-mock1", "mock1", new LinkedList<Dependency>(), new InterpreterOption(false), new Properties());
    assertEquals(numInterpreters + 1, factory.get().size());

    InterpreterFactory factory2 = new InterpreterFactory(conf, null, null, null, depResolver, AuthenticationInfo.ANONYMOUS_AUTHENTICATION_INFO);
    assertEquals(numInterpreters + 1, factory2.get().size());
  }

  @Test
  public void testInterpreterAliases() throws IOException, RepositoryException {
    factory = new InterpreterFactory(conf, null, null, null, depResolver, AuthenticationInfo.ANONYMOUS_AUTHENTICATION_INFO);
    final InterpreterInfo info1 = new InterpreterInfo("className1", "name1", true);
    final InterpreterInfo info2 = new InterpreterInfo("className2", "name1", true);
    factory.add("group1", new ArrayList<InterpreterInfo>(){{
      add(info1);
    }}, new ArrayList<Dependency>(), new InterpreterOption(true), new Properties(), "/path1");
    factory.add("group2", new ArrayList<InterpreterInfo>(){{
      add(info2);
    }}, new ArrayList<Dependency>(), new InterpreterOption(true), new Properties(), "/path2");

    final InterpreterSetting setting1 = factory.createNewSetting("test-group1", "group1", new ArrayList<Dependency>(), new InterpreterOption(true), new Properties());
    final InterpreterSetting setting2 = factory.createNewSetting("test-group2", "group1", new ArrayList<Dependency>(), new InterpreterOption(true), new Properties());

    factory.setInterpreters("note", new ArrayList<String>() {{
      add(setting1.getId());
      add(setting2.getId());
    }});

    assertEquals("className1", factory.getInterpreter("note", "test-group1").getClassName());
    assertEquals("className1", factory.getInterpreter("note", "group1").getClassName());
  }

  @Test
  public void testInvalidInterpreterSettingName() {
    try {
      factory.createNewSetting("new.mock1", "mock1", new LinkedList<Dependency>(), new InterpreterOption(false), new Properties());
      fail("expect fail because of invalid InterpreterSetting Name");
    } catch (IOException e) {
      assertEquals("'.' is invalid for InterpreterSetting name.", e.getMessage());
    }
  }
}
