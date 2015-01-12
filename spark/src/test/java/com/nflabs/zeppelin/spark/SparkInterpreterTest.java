package com.nflabs.zeppelin.spark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;


public class SparkInterpreterTest {
	public static SparkInterpreter repl;

	@Before
	public void setUp() throws Exception {
	  if (repl == null) {
		  Properties p = new Properties();

	    repl = new SparkInterpreter(p);
  	  repl.open();
	  }
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testBasicIntp() {
		assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret("val a = 1\nval b = 2", null).code());

		// when interpret incomplete expression
		InterpreterResult incomplete = repl.interpret("val a = \"\"\"", null);
		assertEquals(InterpreterResult.Code.INCOMPLETE, incomplete.code());
		assertTrue(incomplete.message().length()>0); // expecting some error message
		/*
		assertEquals(1, repl.getValue("a"));
		assertEquals(2, repl.getValue("b"));
		repl.interpret("val ver = sc.version");
		assertNotNull(repl.getValue("ver"));
		assertEquals("HELLO\n", repl.interpret("println(\"HELLO\")").message());
		*/
	}

	@Test
	public void testEndWithComment() {
		assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret("val c=1\n//comment", null).code());
	}

	@Test
	public void testSparkSql(){
		repl.interpret("case class Person(name:String, age:Int)", null);
		repl.interpret("val people = sc.parallelize(Seq(Person(\"moon\", 33), Person(\"jobs\", 51), Person(\"gates\", 51), Person(\"park\", 34)))", null);
		assertEquals(Code.SUCCESS, repl.interpret("people.take(3)", null).code());

		// create new interpreter
		Properties p = new Properties();
		SparkInterpreter repl2 = new SparkInterpreter(p);
		repl2.open();

		repl.interpret("case class Man(name:String, age:Int)", null);
		repl.interpret("val man = sc.parallelize(Seq(Man(\"moon\", 33), Man(\"jobs\", 51), Man(\"gates\", 51), Man(\"park\", 34)))", null);
		assertEquals(Code.SUCCESS, repl.interpret("man.take(3)", null).code());
		repl2.getSparkContext().stop();
	}

	@Test
	public void testReferencingUndefinedVal(){
		InterpreterResult result = repl.interpret("def category(min: Int) = {" +
				       "    if (0 <= value) \"error\"" +
                       "}", null);
		assertEquals(Code.ERROR, result.code());
		System.out.println("msg="+result.message());
	}
}
