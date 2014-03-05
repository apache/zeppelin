package com.nflabs.zeppelin.zengine;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.log4j.Logger;

import com.sun.script.jruby.JRubyScriptEngineFactory;

public class ERBEvaluator {
	Logger logger = Logger.getLogger(ERBEvaluator.class);
	Map<String, Object> localVariables = new HashMap<String, Object>();
	
	private static ScriptEngine _rubyScriptEngine;
	private static Long _rubyScriptEngineCreationLock = new Long(0);

	private ScriptEngine getSingletonScriptEngine(){
		if ( _rubyScriptEngine == null ) {
			synchronized(_rubyScriptEngineCreationLock){
				if ( _rubyScriptEngine == null ) {
					JRubyScriptEngineFactory factory = new JRubyScriptEngineFactory();
					_rubyScriptEngine = factory.getScriptEngine();
					StringBuffer rubyScript = new StringBuffer();
					rubyScript.append("require 'erb'\n");
					try {
						_rubyScriptEngine.eval(rubyScript.toString());
					} catch (ScriptException e) {
						e.printStackTrace();
					}
				}
			}
		}

		return _rubyScriptEngine;
	}

	public String eval(String erbString, Object zcontext) throws ZException{
		ByteArrayInputStream ins = new ByteArrayInputStream(erbString.getBytes());
		BufferedReader erb = new BufferedReader(new InputStreamReader(ins));

		String q = eval(erb, zcontext);
		try {
			ins.close();
		} catch (IOException e) {
			throw new ZException(e);
		}
		return q;
	}
	
	public String eval(BufferedReader erb, Object zcontext) throws ZException{
		ScriptEngine rubyScriptEngine = getSingletonScriptEngine();
		synchronized(rubyScriptEngine){
			StringBuffer rubyScript = new StringBuffer();
			Bindings bindings = rubyScriptEngine.createBindings();
			if (zcontext != null) {
				bindings.put("_zpZ", zcontext);
				rubyScript.append("z = $_zpZ\n");
			}
			bindings.put("_zpLV", localVariables);
			for (String k : localVariables.keySet()) {
				rubyScript.append(k + "= $_zpLV.get(\"" + k + "\")\n");
			}

			rubyScriptEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
			try {
				String line = null;
				rubyScript.append("$_zpErb = \"\"\n");
	
				boolean first = true;
				while((line = erb.readLine())!=null){
					String newline;
					if(first==false){
						newline = "\\n";
					} else {
						newline = "";
						first = false;
					}
					rubyScript.append("$_zpErb += \""+newline+line.replaceAll("\"", "\\\\\"")+"\"\n");
				}
				rubyScript.append("$_zpErb += \"<% local_variables.each do |xx|\n    if xx != 'z' and xx != '_erbout' then $_zpLV[xx] = eval(xx) end\nend %>\"\n");
			} catch (IOException e1) {
				throw new ZException(e1);
			}
			rubyScript.append("$_zpE = ERB.new($_zpErb, \"<>-\").result(binding)\n");
	        try {
	        	logger.debug("rubyScript to run : \n"+rubyScript.toString());
	        	rubyScriptEngine.eval(rubyScript.toString(), bindings);
			} catch (ScriptException e) {
				throw new ZException(e);
			}	        
	        String q = (String) bindings.get("_zpE");

	        return nonNullString(q);
		}
	}

	private String nonNullString(String q) {
	    return q == null ? "" : q;
	}
}
