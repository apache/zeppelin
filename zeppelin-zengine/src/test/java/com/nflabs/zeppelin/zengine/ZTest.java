package com.nflabs.zeppelin.zengine;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.hadoop.hive.conf.HiveConf.ConfVars;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.zengine.Q;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;
import com.sun.script.jruby.JRubyScriptEngineFactory;

import junit.framework.TestCase;

public class ZTest extends TestCase {

	private File tmpDir;


	protected void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
		tmpDir.mkdir();
		System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_WAREHOUSE.getVarName(), "file://"+tmpDir.getAbsolutePath());
		delete(new File("metastore_db"));
		Z.configure();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
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
	
	
	public void testPipeGetQuery() throws ZException{

		Z z = new Q("select * from bank")
   	    .pipe(new Q("select * from (<%=z."+Q.INPUT_VAR_NAME+"%>) q limit 10"))
	    .pipe(new Q("create view vv as select * from <%=z."+Q.INPUT_VAR_NAME+"%>"));
		
		assertEquals("create view vv as select * from "+z.prev().name(), z.getQuery());
		z.release();
	}
	
	public void testAutogenName() throws ZException{
		Z z1 = new Q("select * from bank");
		Z z2 = new Q("select * from (<%=z."+Q.INPUT_VAR_NAME+"%>) q limit 10");
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
	
	
	public void testShowTables() throws ClassNotFoundException, SQLException, ZException{
		ZeppelinConfiguration conf = new ZeppelinConfiguration();
		Z.configure(conf);
		
		new Q("drop table if exists test").execute();		
		new Q("create table if not exists test(a INT, b STRING)").execute();
		Result results = new Q("show tables").execute().result();
		assertEquals("test", results.getRows().get(0)[0]);
		
		new Q("drop table test").execute();		
	}
	
	
	public void testErb() throws ScriptException{
		ScriptEngineFactory factory = new JRubyScriptEngineFactory();
		ScriptEngine engine = factory.getScriptEngine();
		
		
		String varName = "pelotonAppScriptOut_"+System.currentTimeMillis();
		
		
		StringBuffer rubyScript = new StringBuffer();
		rubyScript.append("require 'erb'\n");
		rubyScript.append("$"+varName+" = ERB.new(\"<%= $hql %> world\").result(binding)\n");
		SimpleBindings bindings = new SimpleBindings();
		engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		bindings.put("hql", "hello");
		engine.eval(rubyScript.toString(), bindings);
		String out = (String) engine.getBindings(ScriptContext.ENGINE_SCOPE).get(varName);
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
