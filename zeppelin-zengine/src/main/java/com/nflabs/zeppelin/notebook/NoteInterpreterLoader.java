package com.nflabs.zeppelin.notebook;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.Interpreter.SchedulingMode;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.interpreter.LazyOpenInterpreter;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

/**
 * Repl loader per note
 */
public class NoteInterpreterLoader {
	private InterpreterFactory factory;
	Map<String, Interpreter> loadedInterpreters;
	static Map<String, Interpreter> loadedInterpretersStatic = Collections.synchronizedMap(new HashMap<String, Interpreter>());
	private boolean staticMode;
	private SchedulerFactory schedulerFactory;
	Map<String, Scheduler> loadedSchedulers;
	static Map<String, Scheduler> loadedSchedulersStatic = Collections.synchronizedMap(new HashMap<String, Scheduler>());
	
	
	public NoteInterpreterLoader(InterpreterFactory factory, SchedulerFactory schedulerFactory, boolean staticMode){
		this.factory = factory;
		this.schedulerFactory = schedulerFactory;
		this.staticMode = staticMode;
		if (staticMode) {
			loadedInterpreters = loadedInterpretersStatic;
			loadedSchedulers = loadedSchedulersStatic;
		} else {
			loadedInterpreters = Collections.synchronizedMap(new HashMap<String, Interpreter>());
			loadedSchedulers = Collections.synchronizedMap(new HashMap<String, Scheduler>());
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
			Interpreter repl = factory.createRepl(name, p);
			
			loadedInterpreters.put(name, new LazyOpenInterpreter(repl));			
			return repl;
		}
	}
	
	public Scheduler getScheduler(String replName) {
		String name = (replName!=null) ? replName : factory.getDefaultInterpreterName();
		Interpreter repl = getRepl(replName);
		if (repl == null ) return null;
		
		if (loadedSchedulers.containsKey(name)) {
			return loadedSchedulers.get(name);
		} else {			
			Scheduler scheduler;
			if(repl.getSchedulingMode()==SchedulingMode.FIFO){
				scheduler = schedulerFactory.createOrGetFIFOScheduler("interpreter_"+repl.hashCode());				
			} else {
				scheduler = schedulerFactory.createOrGetParallelScheduler("interpreter_"+repl.hashCode(), 20);
			}	
			loadedSchedulers.put(name, scheduler);
			return scheduler;
		}
		
		
	}
	
	public void destroyAll(){
		Set<String> keys = loadedInterpreters.keySet();
		for(String k : keys) {
			Interpreter repl = loadedInterpreters.get(k);
			repl.close();
		}
		
		Set<String> schedulers = loadedSchedulers.keySet();
		for(String k : schedulers) {
			Scheduler scheduler = loadedSchedulers.get(k);
			scheduler.stop();
		}
	}
	
}
