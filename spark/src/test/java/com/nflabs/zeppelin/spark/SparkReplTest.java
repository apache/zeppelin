package com.nflabs.zeppelin.spark;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.repl.ReplResult;


public class SparkReplTest {

	private SparkRepl repl;

	@Before
	public void setUp() throws Exception {
		repl = new SparkRepl(new Properties());
		repl.initialize();
	}

	@After
	public void tearDown() throws Exception {
		repl.destroy();
	}

	@Test
	public void testBasicRepl() {
		assertEquals(ReplResult.Code.SUCCESS, repl.interpret("val a = 1\nval b = 2").code());
		assertEquals(1, repl.getValue("a"));
		assertEquals(2, repl.getValue("b"));
		repl.interpret("val ver = sc.version");
		assertNotNull(repl.getValue("ver"));
		assertEquals("HELLO\n", repl.interpret("println(\"HELLO\")").message());
		/*
		repl.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
		Map<String, Object> binder = (Map<String, Object>) repl.getValue("_binder");
		binder.put("out", new PrintStream(new ByteArrayOutputStream()));
		System.err.println(repl.interpret("System.setOut(_binder.get(\"out\").asInstanceOf[java.io.PrintStream])").message());
		System.err.println(repl.interpret("Console.setOut(_binder.get(\"out\").asInstanceOf[java.io.PrintStream])").message());
		//System.err.println(repl.interpret("def println(s:Any){ _binder.get(\"out\").asInstanceOf[java.io.PrintStream]).println(s)}").message());
		//System.err.println(repl.interpret("def print(s:Any){ _binder.get(\"out\").asInstanceOf[java.io.PrintStream]).print(s)}").message());
		 * 
		 */

	}
	
	@Test
	public void testSparkRql(){
		repl.interpret("case class Person(name:String, age:Int)");
		repl.interpret("val people = sc.parallelize(Seq(Person(\"moon\", 33), Person(\"jobs\", 51), Person(\"gates\", 51), Person(\"park\", 34)))");
		repl.interpret("people.registerAsTable(\"people\")");
		repl.interpret("val oldguys = sqlc.sql(\"SELECT name FROM people WHERE age>40\")");
		assertEquals("res1: Array[org.apache.spark.sql.Row] = Array([jobs], [gates])\n", repl.interpret("oldguys.collect()").message());
		
	}
	
}
