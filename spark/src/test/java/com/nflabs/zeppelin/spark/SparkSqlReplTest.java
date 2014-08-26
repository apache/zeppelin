package com.nflabs.zeppelin.spark;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Properties;

import org.apache.spark.sql.catalyst.expressions.Row;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.repl.ReplResult;
import com.nflabs.zeppelin.repl.ReplResult.Type;

public class SparkSqlReplTest {

	private SparkRepl repl;
	private SparkSqlRepl sql;

	@Before
	public void setUp() throws Exception {
		repl = new SparkRepl(new Properties());
		repl.initialize();
		sql = new SparkSqlRepl(new Properties());
		sql.setSparkRepl(repl);
		sql.initialize();
	}

	@After
	public void tearDown() throws Exception {
		repl.destroy();
	}
	@Test
	public void test() {
		repl.interpret("case class Person(name:String, age:Int)");
		repl.interpret("val people = sc.parallelize(Seq(Person(\"moon\", 33), Person(\"jobs\", 51), Person(\"gates\", 51), Person(\"park\", 34)))");
		repl.interpret("people.registerAsTable(\"people\")");
		ReplResult ret = sql.interpret("select name, age from people where age < 40");
		assertEquals(ReplResult.Code.SUCCESS, ret.code());
		assertEquals(Type.TABLE, ret.type());
		assertEquals("name\tage\nmoon\t33\npark\t34\n", ret.message());
		
		assertEquals(ReplResult.Code.ERROR, sql.interpret("select wrong syntax").code());
	}

}
