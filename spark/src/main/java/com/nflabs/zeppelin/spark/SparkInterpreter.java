package com.nflabs.zeppelin.spark;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.repl.SparkILoop;
import org.apache.spark.repl.SparkIMain;
import org.apache.spark.scheduler.ActiveJob;
import org.apache.spark.scheduler.DAGScheduler;
import org.apache.spark.sql.SQLContext;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;

import scala.None;
import scala.Some;
import scala.collection.Iterator;
import scala.collection.mutable.HashSet;
import scala.tools.nsc.Settings;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;

public class SparkInterpreter extends Interpreter {

	private SparkILoop interpreter;
	private SparkIMain intp;
	private SparkContext sc;
	private ByteArrayOutputStream out;
	private SQLContext sqlc;
	

	public SparkInterpreter(Properties property) {
		super(property);
		out = new ByteArrayOutputStream();
	}

	public SparkContext getSparkContext(){
		if(sc==null){
			// save / load sc from common share
			Map<String, Object> share = (Map<String, Object>)getProperty().get("share");
			sc = (SparkContext) share.get("sc");
			if(sc==null) {
				sc = createSparkContext();
				share.put("sc", sc);				
			}
		}
		return sc;
	}
	
	public SQLContext getSQLContext(){
		return sqlc;
	}
	
	public SparkContext createSparkContext(){
		System.err.println("------ Create new SparkContext "+getMaster()+" -------");

		String execUri = System.getenv("SPARK_EXECUTOR_URI");
		String[] jars = SparkILoop.getAddedJars();
		SparkConf conf = new SparkConf().setMaster(getMaster())
				.setAppName("Zeppelin").setJars(jars)
				.set("spark.repl.class.uri", interpreter.intp().classServer().uri());
		if (execUri != null) {
			conf.set("spark.executor.uri", execUri);
		}
		if (System.getenv("SPARK_HOME") != null) {
			conf.setSparkHome(System.getenv("SPARK_HOME"));
		}
		SparkContext sparkContext = new SparkContext(conf);
		return sparkContext;
	}
	
	public String getMaster() {
		String envMaster = System.getenv().get("MASTER");
		if(envMaster!=null) return envMaster;
		String propMaster = System.getProperty("spark.master");
		if(propMaster!=null) return propMaster;
		return "local[*]";
	}

	@Override
	public void initialize(){
		// Very nice discussion about how scala compiler handle classpath
		// https://groups.google.com/forum/#!topic/scala-user/MlVwo2xCCI0
		
		/*
		 * > val env = new nsc.Settings(errLogger)
> env.usejavacp.value = true
> val p = new Interpreter(env)
> p.setContextClassLoader
>
Alternatively you can set the class path throuh nsc.Settings.classpath.

>> val settings = new Settings()
>> settings.usejavacp.value = true
>> settings.classpath.value += File.pathSeparator +
>> System.getProperty("java.class.path")
>> val in = new Interpreter(settings) {
>>    override protected def parentClassLoader = getClass.getClassLoader
>> }
>> in.setContextClassLoader()


		 */
		
		Settings settings = new Settings();
		
		// set classpath for scala compiler
		PathSetting pathSettings = settings.classpath();
		String classpath = "";
		List<File> paths = currentClassPath();
		for(File f : paths) {
			if(classpath.length()>0){
				classpath+=File.pathSeparator;
			}
			classpath+=f.getAbsolutePath();
		}
		pathSettings.v_$eq(classpath);
		settings.scala$tools$nsc$settings$ScalaSettings$_setter_$classpath_$eq(pathSettings);

		
		// set classloader for scala compiler
		settings.explicitParentLoader_$eq(new Some<ClassLoader>(Thread.currentThread().getContextClassLoader()));
		BooleanSetting b = (BooleanSetting)settings.usejavacp();
		b.v_$eq(true);
		settings.scala$tools$nsc$settings$StandardScalaSettings$_setter_$usejavacp_$eq(b);
		
		PrintStream printStream = new PrintStream(out);
		
		/* spark interpreter */
		this.interpreter = new SparkILoop(null, new PrintWriter(out));
		interpreter.settings_$eq(settings);
		
		interpreter.createInterpreter();

		intp = interpreter.intp();
		intp.setContextClassLoader();
		intp.initializeSynchronous();


		sc = getSparkContext();

		sqlc = new SQLContext(sc);
		
		intp.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
		Map<String, Object> binder = (Map<String, Object>) getValue("_binder");
		binder.put("out", printStream);
		binder.put("sc", sc);
		binder.put("sqlc", sqlc);

		intp.interpret("@transient val sc = _binder.get(\"sc\").asInstanceOf[org.apache.spark.SparkContext]");
		intp.interpret("import org.apache.spark.SparkContext._");
		intp.interpret("@transient val sqlc = _binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SQLContext]");
		intp.interpret("import sqlc.createSchemaRDD");
	}

	
	private List<File> currentClassPath(){
		List<File> paths = classPath(Thread.currentThread().getContextClassLoader());
		String[] cps = System.getProperty("java.class.path").split(File.pathSeparator);
		if(cps!=null) {
			for(String cp : cps) {
				paths.add(new File(cp));
			}
		}		
		return paths;
	}
	
	private List<File> classPath(ClassLoader cl){
		List<File> paths = new LinkedList<File>();
		if(cl==null)return paths;
		
		if(cl instanceof URLClassLoader) {
			URLClassLoader ucl = (URLClassLoader) cl;
			URL [] urls = ucl.getURLs();
			if(urls!=null) {
				for(URL url : urls) {
					paths.add(new File(url.getFile()));
				}
			}
		} 
		return paths;
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
	public InterpreterResult interpret(String line){
		return interpret(line.split("\n"));
	}
	
	public InterpreterResult interpret(String [] lines){
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
					return new InterpreterResult(r, out.toString());
				} else if(r==Code.INCOMPLETE) {
					incomplete += s +"\n";
				} else {
					incomplete = "";
				}
			}
			sc.clearJobGroup();
			return new InterpreterResult(r, out.toString());
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
