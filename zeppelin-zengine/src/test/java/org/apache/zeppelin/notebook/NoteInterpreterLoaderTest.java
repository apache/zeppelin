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
    System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "org.apache.zeppelin.interpreter.mock.MockInterpreter1,org.apache.zeppelin.interpreter.mock.MockInterpreter11,org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    conf = ZeppelinConfiguration.create();

    Interpreter.registeredInterpreters = Collections
        .synchronizedMap(new HashMap<String, Interpreter.RegisteredInterpreter>());
    MockInterpreter1.register("mock1", "group1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1");
    MockInterpreter11.register("mock11", "group1", "org.apache.zeppelin.interpreter.mock.MockInterpreter11");
    MockInterpreter2.register("mock2", "group2", "org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    depResolver = new DependencyResolver(tmpDir.getAbsolutePath() + "/local-repo");
    factory = new InterpreterFactory(conf, new InterpreterOption(false), null, null, depResolver);
  }

  @After
  public void tearDown() throws Exception {
    delete(tmpDir);
    Interpreter.registeredInterpreters.clear();
  }

  @Test
  public void testGetInterpreter() throws IOException {
    NoteInterpreterLoader loader = new NoteInterpreterLoader(factory);
    loader.setNoteId("note");
    loader.setInterpreters(factory.getDefaultInterpreterSettingList());

    // when there're no interpreter selection directive
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter1", loader.get(null).getClassName());
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter1", loader.get("").getClassName());
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter1", loader.get(" ").getClassName());

    // when group name is omitted
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter11", loader.get("mock11").getClassName());

    // when 'name' is ommitted
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter1", loader.get("group1").getClassName());
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter2", loader.get("group2").getClassName());

    // when nothing is ommitted
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter1", loader.get("group1.mock1").getClassName());
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter11", loader.get("group1.mock11").getClassName());
    assertEquals("org.apache.zeppelin.interpreter.mock.MockInterpreter2", loader.get("group2.mock2").getClassName());

    loader.close();
  }

  @Test
  public void testNoteSession() throws IOException {
    NoteInterpreterLoader loaderA = new NoteInterpreterLoader(factory);
    loaderA.setNoteId("noteA");
    loaderA.setInterpreters(factory.getDefaultInterpreterSettingList());
    loaderA.getInterpreterSettings().get(0).getOption().setPerNoteSession(true);

    NoteInterpreterLoader loaderB = new NoteInterpreterLoader(factory);
    loaderB.setNoteId("noteB");
    loaderB.setInterpreters(factory.getDefaultInterpreterSettingList());
    loaderB.getInterpreterSettings().get(0).getOption().setPerNoteSession(true);

    // interpreters are not created before accessing it
    assertNull(loaderA.getInterpreterSettings().get(0).getInterpreterGroup("shared_process").get("noteA"));
    assertNull(loaderB.getInterpreterSettings().get(0).getInterpreterGroup("shared_process").get("noteB"));

    loaderA.get(null).open();
    loaderB.get(null).open();

    assertTrue(
        loaderA.get(null).getInterpreterGroup().getId().equals(
        loaderB.get(null).getInterpreterGroup().getId()));

    // interpreters are created after accessing it
    assertNotNull(loaderA.getInterpreterSettings().get(0).getInterpreterGroup("shared_process").get("noteA"));
    assertNotNull(loaderB.getInterpreterSettings().get(0).getInterpreterGroup("shared_process").get("noteB"));

    // when
    loaderA.close();
    loaderB.close();

    // interpreters are destroyed after close
    assertNull(loaderA.getInterpreterSettings().get(0).getInterpreterGroup("shared_process").get("noteA"));
    assertNull(loaderB.getInterpreterSettings().get(0).getInterpreterGroup("shared_process").get("noteB"));

  }

  @Test
  public void testNotePerInterpreterProcess() throws IOException {
    NoteInterpreterLoader loaderA = new NoteInterpreterLoader(factory);
    loaderA.setNoteId("noteA");
    loaderA.setInterpreters(factory.getDefaultInterpreterSettingList());
    loaderA.getInterpreterSettings().get(0).getOption().setPerNoteProcess(true);

    NoteInterpreterLoader loaderB = new NoteInterpreterLoader(factory);
    loaderB.setNoteId("noteB");
    loaderB.setInterpreters(factory.getDefaultInterpreterSettingList());
    loaderB.getInterpreterSettings().get(0).getOption().setPerNoteProcess(true);

    // interpreters are not created before accessing it
    assertNull(loaderA.getInterpreterSettings().get(0).getInterpreterGroup("noteA").get("noteA"));
    assertNull(loaderB.getInterpreterSettings().get(0).getInterpreterGroup("noteB").get("noteB"));

    loaderA.get(null).open();
    loaderB.get(null).open();

    // per note interpreter process
    assertFalse(
        loaderA.get(null).getInterpreterGroup().getId().equals(
        loaderB.get(null).getInterpreterGroup().getId()));

    // interpreters are created after accessing it
    assertNotNull(loaderA.getInterpreterSettings().get(0).getInterpreterGroup("noteA").get("noteA"));
    assertNotNull(loaderB.getInterpreterSettings().get(0).getInterpreterGroup("noteB").get("noteB"));

    // when
    loaderA.close();
    loaderB.close();

    // interpreters are destroyed after close
    assertNull(loaderA.getInterpreterSettings().get(0).getInterpreterGroup("noteA").get("noteA"));
    assertNull(loaderB.getInterpreterSettings().get(0).getInterpreterGroup("noteB").get("noteB"));
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
