package com.nflabs.zeppelin.zengine;

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

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.zengine.Q;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;
import com.sun.script.jruby.JRubyScriptEngineFactory;

import junit.framework.TestCase;

public class ZTest extends TestCase {

	protected void setUp() throws Exception {
		Z.configure();
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	
	public void testPipeGetQuery() throws ZException{

		Z z = new Q("select * from bank")
   	    .pipe(new Q("select * from (<%=z."+Q.PREV_VAR_NAME+"%>) q limit 10"))
	    .pipe(new Q("create view vv as select * from <%=z."+Q.PREV_VAR_NAME+"%>"));
		
		assertEquals("CREATE VIEW "+z.name()+" AS create view vv as select * from "+z.prev().name(), z.getQuery());
		z.clean();
	}
	
	public void testShowTables() throws ClassNotFoundException, SQLException, ZException{
		ZeppelinConfiguration conf = new ZeppelinConfiguration();
		Z.configure(conf);
		
		new Q("create table if not exists test(a INT, b STRING)").withName(null).execute();
		Result results = new Q("show tables").withName(null).execute().result();
		assertEquals("test", results.getRows().get(0)[0]);
		
		new Q("drop table test").withName(null).execute();		
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
