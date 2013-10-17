package com.nflabs.zeppelin.zql;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.sun.script.jruby.JRubyScriptEngineFactory;

import junit.framework.TestCase;

public class ZTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	
	public void testPipeGetQuery() throws ZException{
		assertEquals("create view vv as select * from (select * from bank) q limit 10", new Q("select * from bank")
																	  .pipe(new Q("select * from (${q}) q limit 10"))
																	  .pipe(new Q("create view vv as"))
																	  .getQuery()
		);
	}
	
	public void testShowTables() throws ClassNotFoundException, SQLException, ZException{
		ZeppelinConfiguration conf = new ZeppelinConfiguration();
		Z.init(conf);
		
		new Q("create table if not exists test(a INT, b STRING)").execute();
		List<ResultSet> results = new Q("show tables").execute();
		assertEquals(1, results.size());
		results.get(0).next();
		assertEquals("test", results.get(0).getString(1));
		
		results = new Q("drop table test; show tables").execute();
		assertEquals(2, results.size());
		assertFalse(results.get(1).next());
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
