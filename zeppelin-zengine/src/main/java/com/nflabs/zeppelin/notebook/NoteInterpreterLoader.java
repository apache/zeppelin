package com.nflabs.zeppelin.notebook;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.interpreter.LazyOpenInterpreter;

/**
 * Repl loader per note
 */
public class NoteInterpreterLoader {
	private InterpreterFactory factory;
	Map<String, Interpreter> loadedInterpreters;
	static Map<String, Interpreter> loadedInterpretersStatic = Collections.synchronizedMap(new HashMap<String, Interpreter>());
	private boolean staticMode;
	
	public NoteInterpreterLoader(InterpreterFactory factory, boolean staticMode){
		this.factory = factory;
		this.staticMode = staticMode;
		if (staticMode) {
			loadedInterpreters = loadedInterpretersStatic;
		} else {
			loadedInterpreters = Collections.synchronizedMap(new HashMap<String, Interpreter>());
		}
	}
	
	public boolean isStaticMode (){
		return staticMode;
	}
	
	public synchronized Interpreter getRepl(String replName){
		String name = (replName!=null) ? replName : factory.getDefaultInterpreterName();
		if(loadedInterpreters.containsKey(name)) {
			return loadedInterpreters.get(name);
		} else {
			Properties p = new Properties();
			p.put("noteIntpLoader", this);
			Interpreter repl = factory.createRepl(name, p);
			LazyOpenInterpreter lazyIntp = new LazyOpenInterpreter(repl);
			loadedInterpreters.put(name, lazyIntp);			
			return lazyIntp;
		}
	}
	
	public void destroyAll(){
		if(staticMode){
			// not destroying when it is static mode
			return;
		}
		
		Set<String> keys = loadedInterpreters.keySet();
		for(String k : keys) {
			Interpreter repl = loadedInterpreters.get(k);
			repl.close();
			repl.destroy();
			loadedInterpreters.remove(k);
		}
	}
	
}
