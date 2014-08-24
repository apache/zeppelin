package com.nflabs.zeppelin.server;

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

	Map<String, Repl> loadedRepls = new HashMap<String, Repl>();
	
	public NoteReplLoader(ReplFactory factory){
		this.factory = factory;
	}
	
	
	public Repl getRepl(String name, Properties properties){
		synchronized(loadedRepls) {
			if(loadedRepls.containsKey(name)) {
				return loadedRepls.get(name);
			} else {
				Repl repl = factory.createRepl(name, properties);
				loadedRepls.put(name, repl);
				return repl;
			}			
		}		
	}
	
	public void destroyAll(){
		// TODO destroyAll	
	}
	
}
