package com.nflabs.zeppelin.notebook;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;

/**
 * Repl loader per note
 */
public class NoteReplLoader {
	private InterpreterFactory factory;

	Map<String, Interpreter> loadedRepls = Collections.synchronizedMap(new HashMap<String, Interpreter>());
	
	public NoteReplLoader(InterpreterFactory factory){
		this.factory = factory;
	}
	
	
	public Interpreter getRepl(String replName){
		String name = (replName!=null) ? replName : factory.getDefaultReplName();
		if(loadedRepls.containsKey(name)) {
			return loadedRepls.get(name);
		} else {
			Properties p = new Properties();
			p.put("repls", loadedRepls);              // for SparkSqlRepl to use SparkRepl
			Interpreter repl = factory.createRepl(name, p);
			repl.initialize();
			
			loadedRepls.put(name, repl);
			return repl;
		}
	}
	
	public void destroyAll(){
		Set<String> keys = loadedRepls.keySet();
		for(String k : keys) {
			Interpreter repl = loadedRepls.get(k);
			repl.destroy();
		}
	}
	
}
