/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.groovy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.File;
import java.util.*;
/*
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
*/
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.codehaus.groovy.runtime.StackTraceUtils;

/**
 * Groovy interpreter for Zeppelin.
 */
public class GroovyInterpreter extends Interpreter {
	Logger log = LoggerFactory.getLogger(GroovyInterpreter.class);
    GroovyShell shell = null; //new GroovyShell();
    
	public GroovyInterpreter(Properties property) {
		super(property);
	}
	
	@Override
	public void open() {
        CompilerConfiguration conf = new CompilerConfiguration();
        conf.setDebug(true);
        shell = new GroovyShell(conf);
        String classes = getProperty("GROOVY_CLASSES");
        if(classes==null || classes.length()==0){
        	try {
				File jar = new File(GroovyInterpreter.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
    		    classes = new File(jar.getParentFile(),"classes").toString();
        	}catch(Exception e){}
        }
        log.info("groovy classes classpath: "+classes);
        if(classes!=null && classes.length()>0){
	        shell.getClassLoader().addClasspath(classes);
        }
	}
  
	@Override
	public void close() {
		shell = null;
	}
	
	@Override
	public FormType getFormType() {
		return FormType.NONE;
	}

	@Override
	public int getProgress(InterpreterContext context) {
		return 0;
	}

	@Override
	public Scheduler getScheduler() {
		return SchedulerFactory.singleton().createOrGetParallelScheduler(GroovyInterpreter.class.getName() + this.hashCode(), 10);
	}
	
	private Job getRunningJob(String paragraphId) {
		Job foundJob = null;
		Collection<Job> jobsRunning = getScheduler().getJobsRunning();
		for (Job job : jobsRunning) {
			if (job.getId().equals(paragraphId)) {
				foundJob = job;
			}
		}
		return foundJob;
	}

	@Override
	public List<InterpreterCompletion> completion(String buf, int cursor) {
		return null;
	}
	
	Map<String,Class<Script>> scriptCache = Collections.synchronizedMap( new WeakHashMap(1000) );
	Script getGroovyScript(String id, String scriptText) /*throws SQLException*/ {
		if(shell==null){
			throw new RuntimeException("Groovy Shell is not initialized: null");
		}
		try{
			Class<Script> clazz = scriptCache.get(scriptText);
			if(clazz==null){
				String scriptName=id+"_"+Long.toHexString(scriptText.hashCode())+".groovy";
				clazz = (Class<Script>) shell.parse(scriptText, scriptName).getClass();
				scriptCache.put(scriptText,clazz);
			}
			
			Script script=(Script)clazz.newInstance();
			return script;
		}catch(Throwable t){
			throw new RuntimeException("Failed to parse groovy script: "+t,t);
		}
	}
	

	@Override
	public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
		try {
			Script script = getGroovyScript(contextInterpreter.getParagraphId(), cmd);
			Job runningJob = getRunningJob(contextInterpreter.getParagraphId());
			runningJob.info().put("CURRENT_THREAD", Thread.currentThread()); //to be able to terminate thread
			Map bindings = script.getBinding().getVariables();
			bindings.clear();
			StringWriter out = new StringWriter( (int) (cmd.length()*1.75) );
			
			bindings.put("g", new GObject(log, out, this.getProperty(), contextInterpreter) );
			bindings.put("out", new PrintWriter(out, true));
			script.run();
			bindings.clear();
			InterpreterResult result = new InterpreterResult(Code.SUCCESS, out.toString());
			log.info("RESULT: "+result);
			return result;
		}catch(Throwable t){
			t = StackTraceUtils.deepSanitize(t);
			String msg = t.toString()+"\n at "+t.getStackTrace()[0];
			log.error("Failed to run script: "+t+"\n" + cmd+"\n", t);
			return new InterpreterResult(Code.ERROR, msg);
		}
	}


	@Override
	public void cancel(InterpreterContext context) {
		Job runningJob = getRunningJob(context.getParagraphId());
		if (runningJob != null) {
			Map<String, Object> info = runningJob.info();
			Object object = info.get("CURRENT_THREAD");
			if (object instanceof Thread) {
				try {
					Thread t = (Thread) object;
					t.dumpStack();
					t.stop();
				}catch(Throwable t){
					log.error("Failed to cancel script: "+t, t);
				}
			}
		}
	}

}
