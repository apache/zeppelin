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
package org.apache.zeppelin.notebook;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter11;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class NoteInterpreterLoaderTest {

  private File tmpDir;
  private ZeppelinConfiguration conf;
  private InterpreterFactory factory;
  private DependencyResolver depResolver;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
    tmpDir.mkdirs();
    new File(tmpDir, "conf").mkdirs();

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), tmpDir.getAbsolutePath());

    conf = ZeppelinConfiguration.create();

    Interpreter.registeredInterpreters = Collections
        .synchronizedMap(new HashMap<String, Interpreter.RegisteredInterpreter>());
    MockInterpreter1.register("mock1", "group1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1", true);
    MockInterpreter11.register("mock11", "group1", "org.apache.zeppelin.interpreter.mock.MockInterpreter11");
    MockInterpreter2.register("mock2", "group2", "org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    depResolver = new DependencyResolver(tmpDir.getAbsolutePath() + "/local-repo");
    factory = new InterpreterFactory(conf, new InterpreterOption(false), null, null, null, depResolver);
  }

  @After
  public void tearDown() throws Exception {
    delete(tmpDir);
    Interpreter.registeredInterpreters.clear();
  }

  @Test
  public void testGetInterpreter() throws IOException {
    factory.setInterpreters("note", factory.getDefaultInterpreterSettingList());

    // when there're no interpreter selection directive
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter1", factory.getInterpreter("note", null).getClassName());
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter1", factory.getInterpreter("note", "").getClassName());
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter1", factory.getInterpreter("note", " ").getClassName());

    // when group name is omitted
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter11", factory.getInterpreter("note", "mock11").getClassName());

    // when 'name' is ommitted
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter1", factory.getInterpreter("note", "group1").getClassName());
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter2", factory.getInterpreter("note", "group2").getClassName());

    // when nothing is ommitted
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter1", factory.getInterpreter("note", "group1.mock1").getClassName());
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter11", factory.getInterpreter("note", "group1.mock11").getClassName());
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter2", factory.getInterpreter("note", "group2.mock2").getClassName());

    factory.closeNote("note");
  }

  @Test
  public void testNoteSession() throws IOException {
    factory.setInterpreters("noteA", factory.getDefaultInterpreterSettingList());
    factory.getInterpreterSettings("noteA").get(0).getOption().setPerNoteSession(true);

    factory.setInterpreters("noteB", factory.getDefaultInterpreterSettingList());
    factory.getInterpreterSettings("noteB").get(0).getOption().setPerNoteSession(true);

    // interpreters are not created before accessing it
    assertNull(factory.getInterpreterSettings("noteA").get(0).getInterpreterGroup("shared_process").get("noteA"));
    assertNull(factory.getInterpreterSettings("noteB").get(0).getInterpreterGroup("shared_process").get("noteB"));

    factory.getInterpreter("noteA", null).open();
    factory.getInterpreter("noteB", null).open();

    assertTrue(
        factory.getInterpreter("noteA", null).getInterpreterGroup().getId().equals(
        factory.getInterpreter("noteB", null).getInterpreterGroup().getId()));

    // interpreters are created after accessing it
    assertNotNull(factory.getInterpreterSettings("noteA").get(0).getInterpreterGroup("shared_process").get("noteA"));
    assertNotNull(factory.getInterpreterSettings("noteB").get(0).getInterpreterGroup("shared_process").get("noteB"));

    // when
    factory.closeNote("noteA");
    factory.closeNote("noteB");

    // interpreters are destroyed after close
    assertNull(factory.getInterpreterSettings("noteA").get(0).getInterpreterGroup("shared_process").get("noteA"));
    assertNull(factory.getInterpreterSettings("noteB").get(0).getInterpreterGroup("shared_process").get("noteB"));

  }

  @Test
  public void testNotePerInterpreterProcess() throws IOException {
    factory.setInterpreters("noteA", factory.getDefaultInterpreterSettingList());
    factory.getInterpreterSettings("noteA").get(0).getOption().setPerNoteProcess(true);

    factory.setInterpreters("noteB", factory.getDefaultInterpreterSettingList());
    factory.getInterpreterSettings("noteB").get(0).getOption().setPerNoteProcess(true);

    // interpreters are not created before accessing it
    assertNull(factory.getInterpreterSettings("noteA").get(0).getInterpreterGroup("noteA").get("noteA"));
    assertNull(factory.getInterpreterSettings("noteB").get(0).getInterpreterGroup("noteB").get("noteB"));

    factory.getInterpreter("noteA", null).open();
    factory.getInterpreter("noteB", null).open();

    // per note interpreter process
    assertFalse(
        factory.getInterpreter("noteA", null).getInterpreterGroup().getId().equals(
        factory.getInterpreter("noteB", null).getInterpreterGroup().getId()));

    // interpreters are created after accessing it
    assertNotNull(factory.getInterpreterSettings("noteA").get(0).getInterpreterGroup("noteA").get("noteA"));
    assertNotNull(factory.getInterpreterSettings("noteB").get(0).getInterpreterGroup("noteB").get("noteB"));

    // when
    factory.closeNote("noteA");
    factory.closeNote("noteB");

    // interpreters are destroyed after close
    assertNull(factory.getInterpreterSettings("noteA").get(0).getInterpreterGroup("noteA").get("noteA"));
    assertNull(factory.getInterpreterSettings("noteB").get(0).getInterpreterGroup("noteB").get("noteB"));
  }


  private void delete(File file){
    if(file.isFile()) file.delete();
    else if(file.isDirectory()){
      File [] files = file.listFiles();
      if(files!=null && files.length>0){
        for(File f : files){
          delete(f);
        }
      }
      file.delete();
    }
  }
}
