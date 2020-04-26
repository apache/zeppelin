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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.lifecycle.NullLifecycleManager;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.junit.Test;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.repository.RemoteRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;


public class InterpreterSettingManagerTest extends AbstractInterpreterTest {

  @Test
  public void testInitInterpreterSettingManager() throws IOException, RepositoryException {
    assertEquals(6, interpreterSettingManager.get().size());
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    assertEquals("test", interpreterSetting.getName());
    assertEquals("test", interpreterSetting.getGroup());
    assertTrue(interpreterSetting.getLifecycleManager() instanceof NullLifecycleManager);
    assertEquals(8, interpreterSetting.getInterpreterInfos().size());
    // 3 other builtin properties:
    //   * zeppelin.interpreter.output.limit
    //   * zeppelin.interpreter.localRepo
    //   * zeppelin.interpreter.max.poolsize
    assertEquals(6, interpreterSetting.getJavaProperties().size());
    assertEquals("value_1", interpreterSetting.getJavaProperties().getProperty("property_1"));
    assertEquals("new_value_2", interpreterSetting.getJavaProperties().getProperty("property_2"));
    assertEquals("value_3", interpreterSetting.getJavaProperties().getProperty("property_3"));
    assertEquals("shared", interpreterSetting.getOption().perNote);
    assertEquals("shared", interpreterSetting.getOption().perUser);
    assertEquals(0, interpreterSetting.getDependencies().size());
    assertNotNull(interpreterSetting.getAngularObjectRegistryListener());
    assertNotNull(interpreterSetting.getRemoteInterpreterProcessListener());
    assertNotNull(interpreterSetting.getAppEventListener());
    assertNotNull(interpreterSetting.getDependencyResolver());
    assertNotNull(interpreterSetting.getInterpreterSettingManager());

    List<RemoteRepository> repositories = interpreterSettingManager.getRepositories();
    assertEquals(2, repositories.size());
    assertEquals("central", repositories.get(0).getId());

    // Load it again
    InterpreterSettingManager interpreterSettingManager2 = new InterpreterSettingManager(conf,
        mock(AngularObjectRegistryListener.class), mock(RemoteInterpreterProcessListener.class), mock(ApplicationEventListener.class));
    assertEquals(6, interpreterSettingManager2.get().size());
    interpreterSetting = interpreterSettingManager2.getByName("test");
    assertEquals("test", interpreterSetting.getName());
    assertEquals("test", interpreterSetting.getGroup());
    assertEquals(8, interpreterSetting.getInterpreterInfos().size());
    assertEquals(6, interpreterSetting.getJavaProperties().size());
    assertEquals("value_1", interpreterSetting.getJavaProperties().getProperty("property_1"));
    assertEquals("new_value_2", interpreterSetting.getJavaProperties().getProperty("property_2"));
    assertEquals("value_3", interpreterSetting.getJavaProperties().getProperty("property_3"));
    assertEquals("shared", interpreterSetting.getOption().perNote);
    assertEquals("shared", interpreterSetting.getOption().perUser);
    assertEquals(0, interpreterSetting.getDependencies().size());

    repositories = interpreterSettingManager2.getRepositories();
    assertEquals(2, repositories.size());
    assertEquals("central", repositories.get(0).getId());

  }

  @Test
  public void testCreateUpdateRemoveSetting() throws IOException, InterpreterException {
    // create new interpreter setting
    InterpreterOption option = new InterpreterOption();
    option.setPerNote("scoped");
    option.setPerUser("scoped");
    Map<String, InterpreterProperty> properties = new HashMap<>();
    properties.put("property_4", new InterpreterProperty("property_4","value_4"));

    try {
      interpreterSettingManager.createNewSetting("test2", "test", new ArrayList<Dependency>(), option, properties);
      fail("Should fail due to interpreter already existed");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("already existed"));
    }

    interpreterSettingManager.createNewSetting("test3", "test", new ArrayList<Dependency>(), option, properties);
    assertEquals(7, interpreterSettingManager.get().size());
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test3");
    assertEquals("test3", interpreterSetting.getName());
    assertEquals("test", interpreterSetting.getGroup());
    // 3 other builtin properties:
    //   * zeppelin.interpeter.output.limit
    //   * zeppelin.interpreter.localRepo
    //   * zeppelin.interpreter.max.poolsize
    assertEquals(4, interpreterSetting.getJavaProperties().size());
    assertEquals("value_4", interpreterSetting.getJavaProperties().getProperty("property_4"));
    assertEquals("scoped", interpreterSetting.getOption().perNote);
    assertEquals("scoped", interpreterSetting.getOption().perUser);
    assertEquals(0, interpreterSetting.getDependencies().size());
    assertNotNull(interpreterSetting.getAngularObjectRegistryListener());
    assertNotNull(interpreterSetting.getRemoteInterpreterProcessListener());
    assertNotNull(interpreterSetting.getAppEventListener());
    assertNotNull(interpreterSetting.getDependencyResolver());
    assertNotNull(interpreterSetting.getInterpreterSettingManager());

    // load it again, it should be saved in interpreter-setting.json. So we can restore it properly
    InterpreterSettingManager interpreterSettingManager2 = new InterpreterSettingManager(conf,
        mock(AngularObjectRegistryListener.class), mock(RemoteInterpreterProcessListener.class), mock(ApplicationEventListener.class));
    assertEquals(7, interpreterSettingManager2.get().size());
    interpreterSetting = interpreterSettingManager2.getByName("test3");
    assertEquals("test3", interpreterSetting.getName());
    assertEquals("test", interpreterSetting.getGroup());
    assertEquals(4, interpreterSetting.getJavaProperties().size());
    assertEquals("value_4", interpreterSetting.getJavaProperties().getProperty("property_4"));
    assertEquals("scoped", interpreterSetting.getOption().perNote);
    assertEquals("scoped", interpreterSetting.getOption().perUser);
    assertEquals(0, interpreterSetting.getDependencies().size());

    // update interpreter setting
    InterpreterOption newOption = new InterpreterOption();
    newOption.setPerNote("scoped");
    newOption.setPerUser("isolated");
    Map<String, InterpreterProperty> newProperties = new HashMap<>(properties);
    newProperties.put("property_4", new InterpreterProperty("property_4", "new_value_4"));
    List<Dependency> newDependencies = new ArrayList<>();
    newDependencies.add(new Dependency("com.databricks:spark-avro_2.11:3.1.0"));
    interpreterSettingManager.setPropertyAndRestart(interpreterSetting.getId(), newOption, newProperties, newDependencies);
    interpreterSetting = interpreterSettingManager.get(interpreterSetting.getId());
    assertEquals("test3", interpreterSetting.getName());
    assertEquals("test", interpreterSetting.getGroup());
    assertEquals(4, interpreterSetting.getJavaProperties().size());
    assertEquals("new_value_4", interpreterSetting.getJavaProperties().getProperty("property_4"));
    assertEquals("scoped", interpreterSetting.getOption().perNote);
    assertEquals("isolated", interpreterSetting.getOption().perUser);
    assertEquals(1, interpreterSetting.getDependencies().size());
    assertNotNull(interpreterSetting.getAngularObjectRegistryListener());
    assertNotNull(interpreterSetting.getRemoteInterpreterProcessListener());
    assertNotNull(interpreterSetting.getAppEventListener());
    assertNotNull(interpreterSetting.getDependencyResolver());
    assertNotNull(interpreterSetting.getInterpreterSettingManager());

    // restart in note page
    // create 3 sessions as it is scoped mode
    interpreterSetting.getOption().setPerUser("scoped");
    interpreterSetting.getDefaultInterpreter("user1", "note1");
    interpreterSetting.getDefaultInterpreter("user2", "note2");
    interpreterSetting.getDefaultInterpreter("user3", "note3");
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", "note1");
    assertEquals(3, interpreterGroup.getSessionNum());
    // only close user1's session
    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", "note1");
    assertEquals(2, interpreterGroup.getSessionNum());

    // remove interpreter setting
    interpreterSettingManager.remove(interpreterSetting.getId());
    assertEquals(6, interpreterSettingManager.get().size());

    // load it again
    InterpreterSettingManager interpreterSettingManager3 = new InterpreterSettingManager(new ZeppelinConfiguration(),
        mock(AngularObjectRegistryListener.class), mock(RemoteInterpreterProcessListener.class), mock(ApplicationEventListener.class));
    assertEquals(6, interpreterSettingManager3.get().size());

  }

  //@Test
  public void testGetEditor() throws IOException, InterpreterNotFoundException {
    Interpreter echoInterpreter = interpreterFactory.getInterpreter("test.echo", "test", new ExecutionContext("user1", "note1"));
    // get editor setting from interpreter-setting.json
    Map<String, Object> editor = interpreterSettingManager.getEditorSetting("test.echo", "note1");
    assertEquals("java", editor.get("language"));

    // when editor setting doesn't exit, return the default editor
    Interpreter mock1Interpreter = interpreterFactory.getInterpreter("mock1", "test", new ExecutionContext("user1", "note1"));
    editor = interpreterSettingManager.getEditorSetting("mock1", "note1");
    assertEquals("text", editor.get("language"));
  }

  @Test
  public void testRestartShared() throws InterpreterException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser("shared");
    interpreterSetting.getOption().setPerNote("shared");

    interpreterSetting.getOrCreateSession("user1", "note1");
    interpreterSetting.getOrCreateInterpreterGroup("user2", "note2");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", "note1");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testRestartPerUserIsolated() throws InterpreterException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser("isolated");
    interpreterSetting.getOption().setPerNote("shared");

    interpreterSetting.getOrCreateSession("user1", "note1");
    interpreterSetting.getOrCreateSession("user2", "note2");
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());

    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testRestartPerNoteIsolated() throws InterpreterException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser("shared");
    interpreterSetting.getOption().setPerNote("isolated");

    interpreterSetting.getOrCreateSession("user1", "note1");
    interpreterSetting.getOrCreateSession("user2", "note2");
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());

    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testRestartPerUserScoped() throws InterpreterException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser("scoped");
    interpreterSetting.getOption().setPerNote("shared");

    interpreterSetting.getOrCreateSession("user1", "note1");
    interpreterSetting.getOrCreateSession("user2", "note2");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
  }

  @Test
  public void testRestartPerNoteScoped() throws InterpreterException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser("shared");
    interpreterSetting.getOption().setPerNote("scoped");

    interpreterSetting.getOrCreateSession("user1", "note1");
    interpreterSetting.getOrCreateSession("user2", "note2");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
  }
}
