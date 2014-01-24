package com.nflabs.zeppelin.zengine.api;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.junit.After;
import org.junit.Before;

import com.jointhegrid.hive_test.HiveTestService;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.util.UtilsForTests;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.Zengine;
import com.sun.script.jruby.JRubyScriptEngineFactory;

public class ZTest extends HiveTestService {

	public ZTest() throws IOException {
		super();
	}

	private File tmpDir;
    private Zengine z;
    private ZeppelinDriver drv;


    @Before
	public void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
		tmpDir.mkdir();
		
		UtilsForTests.delete(new File("/tmp/warehouse"));
		UtilsForTests.delete(new File(ROOT_DIR.getName()));
				
        //Dependencies: ZeppelinDriver + ZeppelinConfiguration + fs + RubyExecutionEngine
        z = new Zengine();
        z.configure();

        drv = UtilsForTests.createHiveTestDriver(z.getConf(), client);
	}

    @After
	public void tearDown() throws Exception {
		super.tearDown();
		UtilsForTests.delete(tmpDir);
		UtilsForTests.delete(new File("/tmp/warehouse"));
		UtilsForTests.delete(new File(ROOT_DIR.getName()));
	}	
	
	public void testPipeGetQuery() throws ZException{
		Z q = new Q("select * from bank", z, drv)
		    .pipe(new Q("select * from (<%=z."+Q.INPUT_VAR_NAME+"%>) q limit 10", z, drv))
		    .pipe(new Q("create view vv as select * from <%=z."+Q.INPUT_VAR_NAME+"%>", z, drv));
		
		assertEquals("create view vv as select * from "+q.prev().name(), q.getQuery());
		q.release();
	}
	
	public void testAutogenName() throws ZException{
		Z z1 = new Q("select * from bank", z, drv);
		Z z2 = new Q("select * from (<%=z."+Q.INPUT_VAR_NAME+"%>) q limit 10", z, drv);
		assertNull(z1.name());
		z1.pipe(z2);
		assertNull(z2.name());
		assertNotNull(z1.name());
		
		z1.unPipe();
		assertNull(z1.name());
		assertNull(z2.name());
		
		z1.withName("hello");
		z1.pipe(z2);
		z1.unPipe();
		assertEquals("hello", z1.name());
	}
	
	public void testThatErbTempleteRuns() throws ScriptException {
	    //given engine
		ScriptEngineFactory factory = new JRubyScriptEngineFactory();
		ScriptEngine engine = factory.getScriptEngine();
		
		StringBuffer rubyScript = new StringBuffer();
		
		String varName = "pelotonAppScriptOut_"+System.currentTimeMillis();
		rubyScript.append("require 'erb'\n");
		rubyScript.append("$"+varName+" = ERB.new(\"<%= $hql %> world\").result(binding)\n");

		SimpleBindings bindings = new SimpleBindings();
		engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

		//when
		bindings.put("hql", "hello");
		engine.eval(rubyScript.toString(), bindings);

		//then
		String out = (String) bindings.get(varName);
		assertEquals("hello world", out);
	}
	
	public void testRubyBinding() throws ScriptException{
		ScriptEngineFactory factory = new JRubyScriptEngineFactory();
		ScriptEngine engine1 = factory.getScriptEngine();
		SimpleBindings bindings = new SimpleBindings();
		engine1.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		engine1.eval("$x=1", bindings);
		assertEquals(new Long(1), (Long)engine1.getBindings(ScriptContext.ENGINE_SCOPE).get("x"));
		
		ScriptEngine engine2 = factory.getScriptEngine();
		SimpleBindings bindings2 = new SimpleBindings();
		engine2.setBindings(bindings2, ScriptContext.ENGINE_SCOPE);

		assertEquals(null, (Long)engine2.getBindings(ScriptContext.ENGINE_SCOPE).get("x"));
		
	}
	
	public void testJavaMapToRubyHash() throws ScriptException{
		
		ScriptEngineFactory factory = new JRubyScriptEngineFactory();
		ScriptEngine engine1 = factory.getScriptEngine();
		SimpleBindings bindings = new SimpleBindings();
		engine1.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("s", "hello");
		params.put("i", 123);
		
		bindings.put("params", params);
		engine1.eval("require 'erb'\n"+
				     "def convert map, hsh\n"+
		             " map.each{|key,value|hsh[key]=value}\n"+
				     "end\n"+
		             "params= {}\n"+
				     "convert($params, params)\n"+

		             "$e = ERB.new(\"<%= params['s'] %> world\").result(binding)\n"		             
				     , bindings);
		
		assertEquals("hello world", (String)engine1.getBindings(ScriptContext.ENGINE_SCOPE).get("e"));
	}
}
