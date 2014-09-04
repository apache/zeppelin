package com.nflabs.zeppelin.spark;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.interpreter.InterpreterResult;


public class SparkInterpreterTest {
	private SparkInterpreter repl;
	
	@Before
	public void setUp() throws Exception {
		Properties p = new Properties();
		p.put("share", new HashMap<String, Object>());
		repl = new SparkInterpreter(p);
		repl.initialize();
	}

	@After
	public void tearDown() throws Exception {
		repl.getSparkContext().stop();
	}

	@Test
	public void testBasicIntp() {
		/*
		assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret("val a = 1\nval b = 2").code());
		assertEquals(1, repl.getValue("a"));
		assertEquals(2, repl.getValue("b"));
		repl.interpret("val ver = sc.version");
		assertNotNull(repl.getValue("ver"));
		*/
		assertEquals("HELLO\n", repl.interpret("println(\"HELLO\")").message());
	}
	/*
	@Test
	public void testSparkSql(){
		repl.interpret("case class Person(name:String, age:Int)");
		repl.interpret("val people = sc.parallelize(Seq(Person(\"moon\", 33), Person(\"jobs\", 51), Person(\"gates\", 51), Person(\"park\", 34)))");
		repl.interpret("people.registerAsTable(\"people\")");
		System.err.println(repl.interpret("val oldguys = sqlc.sql(\"SELECT name FROM people WHERE age>40\")").message());
		assertEquals("res6: Array[org.apache.spark.sql.Row] = Array([jobs], [gates])\n", repl.interpret("oldguys.collect()").message());
		
	}
	*/
}
