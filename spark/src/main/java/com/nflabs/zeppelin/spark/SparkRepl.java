package com.nflabs.zeppelin.spark;

import java.io.BufferedReader;
import java.io.PrintWriter;

import org.apache.spark.SparkContext;
import org.apache.spark.repl.SparkILoop;
import org.apache.spark.repl.SparkIMain;

import scala.None;
import scala.Some;
import scala.tools.nsc.Settings;

public class SparkRepl {
	private SparkILoop interpreter;
	private SparkIMain intp;
	private SparkContext sc;
	
	public static enum Result {
		SUCCESS,
		INCOMPLETE,
		ERROR
	}

	public SparkRepl(BufferedReader reader, PrintWriter writer){
		Settings settings = new Settings();
		settings.classpath().value_$eq(System.getProperty("java.class.path"));
		
		this.interpreter = new SparkILoop(reader, writer);
		interpreter.settings_$eq(settings);
		
		interpreter.createInterpreter();
		intp = interpreter.intp();
		intp.initializeSynchronous();
		this.sc = interpreter.createSparkContext();
		intp.bindValue("sc", sc);
		
	}
	
	public void bindValue(String name, Object o){
		intp.bindValue("sc", sc);
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
	
	
	
	public Result interpret(String st){
		scala.tools.nsc.interpreter.Results.Result r = intp.interpret(st);
		if (r instanceof scala.tools.nsc.interpreter.Results.Success$) {
			return Result.SUCCESS;
		} else if (r instanceof scala.tools.nsc.interpreter.Results.Incomplete$) {
			return Result.INCOMPLETE;
		} else {
			return Result.ERROR;
		}
	}
	
	

}
