package com.nflabs.zeppelin.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.nflabs.zeppelin.repl.Repl;
import com.nflabs.zeppelin.repl.ReplFactory;

/**
 * Repl loader per note
 */
public class NoteReplLoader {
	private ReplFactory factory;

	Map<String, Repl> loadedRepls = Collections.synchronizedMap(new HashMap<String, Repl>());
	
	public NoteReplLoader(ReplFactory factory){
		this.factory = factory;
	}
	
	
	public Repl getRepl(String name, Properties properties){
		if(loadedRepls.containsKey(name)) {
			return loadedRepls.get(name);
		} else {
			Properties p = new Properties(properties);
			p.put("repls", loadedRepls);
			Repl repl = factory.createRepl(name, p);
			repl.initialize();				
			loadedRepls.put(name, repl);
			return repl;
		}
	}
	
	public void destroyAll(){
		// TODO destroyAll	
	}
	
}
