package com.nflabs.zeppelin.interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.interpreter.mock.MockInterpreter1;
import com.nflabs.zeppelin.interpreter.mock.MockInterpreter2;

public class InterpreterFactoryTest {

	private InterpreterFactory factory;
  private File tmpDir;
  private ZeppelinConfiguration conf;
  private InterpreterContext context;

  @Before
	public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
    tmpDir.mkdirs();
    new File(tmpDir, "conf").mkdirs();

	  MockInterpreter1.register("mock1", "com.nflabs.zeppelin.interpreter.mock.MockInterpreter1");
	  MockInterpreter2.register("mock2", "com.nflabs.zeppelin.interpreter.mock.MockInterpreter2");

	  System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), tmpDir.getAbsolutePath());
	  System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "com.nflabs.zeppelin.interpreter.mock.MockInterpreter1,com.nflabs.zeppelin.interpreter.mock.MockInterpreter2");
	  conf = new ZeppelinConfiguration();
	  factory = new InterpreterFactory(conf, new InterpreterOption(false));
	  context = new InterpreterContext("id", "title", "text", null, null);

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

		// get interpreter
		Interpreter repl1 = factory.get(all.get(0)).getInterpreterGroup().getFirst();
		assertFalse(((LazyOpenInterpreter) repl1).isOpen());
		repl1.interpret("repl1", context);
		assertTrue(((LazyOpenInterpreter) repl1).isOpen());

		// try to get unavailable interpreter
		assertNull(factory.get("unknown"));

		// restart interpreter
		factory.restart(all.get(0));
		repl1 = factory.get(all.get(0)).getInterpreterGroup().getFirst();
		assertFalse(((LazyOpenInterpreter) repl1).isOpen());
	}

  @Test
  public void testFactoryDefaultList() throws InterpreterException, IOException {
    // get default list from default setting
    List<String> all = factory.getDefaultInterpreterSettingList();
    assertEquals(2, all.size());
    assertEquals(factory.get(all.get(0)).getInterpreterGroup().getFirst().getClassName(), "com.nflabs.zeppelin.interpreter.mock.MockInterpreter1");

    // add setting
    factory.add("a mock", "mock2", new InterpreterOption(false), new Properties());
    all = factory.getDefaultInterpreterSettingList();
    assertEquals(2, all.size());
    assertEquals("mock1", factory.get(all.get(0)).getName());
    assertEquals("a mock", factory.get(all.get(1)).getName());
  }

  @Test
  public void testSaveLoad() throws InterpreterException, IOException {
    // interpreter settings
    assertEquals(2, factory.get().size());

    // check if file saved
    assertTrue(new File(conf.getInterpreterSettingPath()).exists());

    factory.add("newsetting", "mock1", new InterpreterOption(false), new Properties());
    assertEquals(3, factory.get().size());

    InterpreterFactory factory2 = new InterpreterFactory(conf);
    assertEquals(3, factory2.get().size());
  }
}
