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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.NullArgumentException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonatype.aether.RepositoryException;

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

    MockInterpreter1.register("mock1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1");
    MockInterpreter2.register("mock2", "org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), tmpDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "org.apache.zeppelin.interpreter.mock.MockInterpreter1,org.apache.zeppelin.interpreter.mock.MockInterpreter2");
    conf = new ZeppelinConfiguration();
    depResolver = new DependencyResolver(tmpDir.getAbsolutePath() + "/local-repo");
    factory = new InterpreterFactory(conf, new InterpreterOption(false), null, null, depResolver);
    context = new InterpreterContext("note", "id", "title", "text", null, null, null, null, null, null, null);

  }

  @After
  public void tearDown() throws Exception {
    delete(tmpDir);
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

  @Test
  public void testBasic() {
    List<String> all = factory.getDefaultInterpreterSettingList();
    InterpreterSetting setting = factory.get(all.get(0));
    InterpreterGroup interpreterGroup = setting.getInterpreterGroup("sharedProcess");
    factory.createInterpretersForNote(setting, "sharedProcess", "session");

    // get interpreter
    Interpreter repl1 = interpreterGroup.get("session").get(0);
    assertFalse(((LazyOpenInterpreter) repl1).isOpen());
    repl1.interpret("repl1", context);
    assertTrue(((LazyOpenInterpreter) repl1).isOpen());

    // try to get unavailable interpreter
    assertNull(factory.get("unknown"));

    // restart interpreter
    factory.restart(all.get(0));
    assertNull(setting.getInterpreterGroup("sharedProcess").get("session"));
  }

  @Test
  public void testFactoryDefaultList() throws IOException, RepositoryException {
    // get default settings
    List<String> all = factory.getDefaultInterpreterSettingList();
    assertEquals(2, all.size());
  }

  @Test
  public void testExceptions() throws InterpreterException, IOException, RepositoryException {
    List<String> all = factory.getDefaultInterpreterSettingList();
    // add setting with null option & properties expected nullArgumentException.class
    try {
      factory.add("a mock", "mock2", new LinkedList<Dependency>(), null, new Properties());
    } catch(NullArgumentException e) {
      assertEquals("Test null option" , e.getMessage(),new NullArgumentException("option").getMessage());
    }
    try {
      factory.add("a mock", "mock2", new LinkedList<Dependency>(), new InterpreterOption(false), null);
    } catch (NullArgumentException e){
      assertEquals("Test null properties" , e.getMessage(),new NullArgumentException("properties").getMessage());
    }
  }


  @Test
  public void testSaveLoad() throws IOException, RepositoryException {
    // interpreter settings
    assertEquals(2, factory.get().size());

    // check if file saved
    assertTrue(new File(conf.getInterpreterSettingPath()).exists());

    factory.add("newsetting", "mock1", new LinkedList<Dependency>(), new InterpreterOption(false), new Properties());
    assertEquals(3, factory.get().size());

    InterpreterFactory factory2 = new InterpreterFactory(conf, null, null, null, depResolver);
    assertEquals(3, factory2.get().size());
  }
}
