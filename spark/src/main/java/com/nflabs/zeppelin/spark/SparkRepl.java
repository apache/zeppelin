package com.nflabs.zeppelin.spark;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;

import org.apache.spark.SparkContext;
import org.apache.spark.repl.SparkILoop;
import org.apache.spark.repl.SparkIMain;
import org.apache.spark.scheduler.ActiveJob;
import org.apache.spark.scheduler.DAGScheduler;

import com.nflabs.zeppelin.repl.Repl;

import scala.None;
import scala.Some;
import scala.collection.Iterator;
import scala.collection.mutable.HashSet;
import scala.tools.nsc.Settings;

public class SparkRepl extends Repl {
	public SparkRepl(Reader reader, Writer writer) {
		super(reader, writer);
	}

	private SparkILoop interpreter;
	private SparkIMain intp;
	private static SparkContext sc;
	private static Long sparkContextCreationLock = new Long(0); 	
	
	@Override
	public void initialize(){
		Settings settings = new Settings();
		settings.classpath().value_$eq(System.getProperty("java.class.path"));
		
		this.interpreter = new SparkILoop(new BufferedReader(getReader()), new PrintWriter(getWriter()));
		interpreter.settings_$eq(settings);
		
		interpreter.createInterpreter();
		intp = interpreter.intp();
		intp.initializeSynchronous();
		
		synchronized(sparkContextCreationLock) {
			if (sc == null) {
				this.sc = interpreter.createSparkContext();
			}
		}
		intp.bindValue("sc", sc);		
	}
	
	public void bindValue(String name, Object o){
		getResult(intp.bindValue("sc", sc));
	}
	
	public Object getValue(String name){
		Object ret = intp.valueOfTerm(name);
		if (ret instanceof None) {
			return null;
		} else if (ret instanceof Some) {
		    return ((Some)ret).get();
		} else {
			return ret;
		}
	}
	
	
	private final String jobGroup = "zeppelin-"+this.hashCode();
	
	public Result interpret(String st){
		synchronized(this){
			String[] stmts = st.split("\n");
			sc.setJobGroup(jobGroup, "Zeppelin", false);
			Result r = null;
			for(String s : stmts) {
				r = getResult(intp.interpret(s));
				if (r == Result.ERROR) {
					sc.clearJobGroup();
					return r;
				}
			}
			sc.clearJobGroup();
			return r;
		}
	}
	
	public void cancel(){
		sc.cancelJobGroup(jobGroup);
	}

	
	public int getProgress(){
		DAGScheduler scheduler = sc.dagScheduler();
		HashSet<ActiveJob> jobs = scheduler.activeJobs();
		Iterator<ActiveJob> it = jobs.iterator();
		while(it.hasNext()) {
			ActiveJob job = it.next();
			String g = (String) job.properties().get("spark.jobGroup.id");
			if (jobGroup.equals(g)) {
				// TODO
			}
		}
		return 0;
	}
	
	private Result getResult(scala.tools.nsc.interpreter.Results.Result r){
		if (r instanceof scala.tools.nsc.interpreter.Results.Success$) {
			return Result.SUCCESS;
		} else if (r instanceof scala.tools.nsc.interpreter.Results.Incomplete$) {
			return Result.INCOMPLETE;
		} else {
			return Result.ERROR;
		}
	}

	@Override
	public void destroy() {

	}
	

}
