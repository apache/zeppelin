package com.nflabs.zeppelin.spark;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.interpreter.ClassloaderInterpreter;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Type;

public class SparkSqlInterpreterTest {

	private SparkInterpreter repl;
	private SparkSqlInterpreter sql;

	@Before
	public void setUp() throws Exception {
		Properties p = new Properties();
		p.put("share", new HashMap<String, Object>());
		repl = new SparkInterpreter(p);
		repl.open();
		sql = new SparkSqlInterpreter(p);
		sql.open();
	}

	@After
	public void tearDown() throws Exception {
		repl.getSparkContext().stop();
	}
	@Test
	public void test() {
		repl.interpret("case class Person(name:String, age:Int)");
		repl.interpret("val people = sc.parallelize(Seq(Person(\"moon\", 33), Person(\"jobs\", 51), Person(\"gates\", 51), Person(\"park\", 34)))");
		repl.interpret("people.registerAsTable(\"people\")");

		InterpreterResult ret = sql.interpret("select name, age from people where age < 40");
		System.err.println("RET="+ret.message());
		assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
		assertEquals(Type.TABLE, ret.type());
		assertEquals("name\tage\nmoon\t33\npark\t34\n", ret.message());
		
		assertEquals(InterpreterResult.Code.ERROR, sql.interpret("select wrong syntax").code());

	}

}
