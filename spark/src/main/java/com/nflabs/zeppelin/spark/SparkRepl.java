package com.nflabs.zeppelin.spark;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

import org.apache.spark.SparkContext;
import org.apache.spark.repl.SparkILoop;
import org.apache.spark.repl.SparkIMain;
import org.apache.spark.scheduler.ActiveJob;
import org.apache.spark.scheduler.DAGScheduler;
import org.apache.spark.sql.SQLContext;

import com.nflabs.zeppelin.repl.Repl;
import com.nflabs.zeppelin.repl.ReplResult;
import com.nflabs.zeppelin.repl.ReplResult.Code;

import scala.None;
import scala.Some;
import scala.collection.Iterator;
import scala.collection.mutable.HashSet;
import scala.tools.nsc.Settings;

public class SparkRepl extends Repl {

	public static SparkILoop interpreter;
	private SparkIMain intp;
	private SparkContext sc;
	private Long sparkContextCreationLock = new Long(0);
	private ByteArrayOutputStream out;
	private SQLContext sqlc;
	

	public SparkRepl(Properties property) {
		super(property);
		out = new ByteArrayOutputStream();
	}

	public SparkContext getSparkContext(){
		return sc;
	}
	
	public SQLContext getSQLContext(){
		return sqlc;
	}

	@Override
	public void initialize(){
		Settings settings = new Settings();
		settings.classpath().value_$eq(System.getProperty("java.class.path"));
		PrintStream printStream = new PrintStream(out);
		this.interpreter = new SparkILoop(null, new PrintWriter(out));
		interpreter.settings_$eq(settings);
		
		interpreter.createInterpreter();
		intp = interpreter.intp();
		intp.initializeSynchronous();
		
		synchronized(sparkContextCreationLock) {
			// redirect stdout
			intp.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
			Map<String, Object> binder = (Map<String, Object>) getValue("_binder");
			binder.put("out", printStream);
			//intp.interpret("System.setOut(_binder.get(\"out\").asInstanceOf[java.io.PrintStream])");
			//intp.interpret("Console.setOut(_binder.get(\"out\").asInstanceOf[java.io.PrintStream])");
			
			intp.interpret("@transient val sc = com.nflabs.zeppelin.spark.SparkRepl.interpreter.createSparkContext()\n");
			intp.interpret("import org.apache.spark.SparkContext._");
			intp.interpret("val sqlc = new org.apache.spark.sql.SQLContext(sc)");
			intp.interpret("import sqlc.createSchemaRDD");
			sc = (SparkContext) getValue("sc");
			sqlc = (SQLContext) getValue("sqlc");
		}
	}
	
	public void bindValue(String name, Object o){
		getResultCode(intp.bindValue(name, o));
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

	/**
	 * Interpret a single line
	 */
	public ReplResult interpret(String line){
		return interpret(line.split("\n"));
	}
	
	public ReplResult interpret(String [] lines){
		synchronized(this){
			intp.interpret("Console.setOut(_binder.get(\"out\").asInstanceOf[java.io.PrintStream])");
			out.reset();
			sc.setJobGroup(jobGroup, "Zeppelin", false);			
			Code r = null;
			String incomplete = "";
			for(String s : lines) {				
				scala.tools.nsc.interpreter.Results.Result res = intp.interpret(incomplete+s);
				r = getResultCode(res);
				
				if (r == Code.ERROR) {
					sc.clearJobGroup();
					return new ReplResult(r, out.toString());
				} else if(r==Code.INCOMPLETE) {
					incomplete += s +"\n";
				} else {
					incomplete = "";
				}
			}
			sc.clearJobGroup();
			return new ReplResult(r, out.toString());
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
	
	private Code getResultCode(scala.tools.nsc.interpreter.Results.Result r){
		if (r instanceof scala.tools.nsc.interpreter.Results.Success$) {
			return Code.SUCCESS;
		} else if (r instanceof scala.tools.nsc.interpreter.Results.Incomplete$) {
			return Code.INCOMPLETE;
		} else {
			return Code.ERROR;
		}
	}

	@Override
	public void destroy() {

	}

	@Override
	public FormType getFormType() {
		return FormType.NATIVE;
	}
	

}
