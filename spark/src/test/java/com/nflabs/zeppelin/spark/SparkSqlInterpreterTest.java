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
	    repl = SparkInterpreter.singleton();
	    if (repl == null) {
	    	repl = new SparkInterpreter(p);
			SparkInterpreter.setSingleton(repl);
			repl.open();
	    }
		sql = new SparkSqlInterpreter(p);
		sql.open();
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public void test() {
		repl.interpret("case class Test(name:String, age:Int)");
		repl.interpret("val test = sc.parallelize(Seq(Test(\"moon\", 33), Test(\"jobs\", 51), Test(\"gates\", 51), Test(\"park\", 34)))");
		repl.interpret("test.registerAsTable(\"test\")");

		InterpreterResult ret = sql.interpret("select name, age from test where age < 40");
		assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
		assertEquals(Type.TABLE, ret.type());
		assertEquals("name\tage\nmoon\t33\npark\t34\n", ret.message());

		assertEquals(InterpreterResult.Code.ERROR, sql.interpret("select wrong syntax").code());
		assertEquals(InterpreterResult.Code.ERROR, sql.interpret("select case when name==\"aa\" then name else name end from people").code());
	}

	@Test
	public void testStruct(){
		repl.interpret("case class Person(name:String, age:Int)");
		repl.interpret("case class People(group:String, person:Person)");
		repl.interpret("val gr = sc.parallelize(Seq(People(\"g1\", Person(\"moon\",33)), People(\"g2\", Person(\"sun\",11))))");
		repl.interpret("gr.registerAsTable(\"gr\")");
		InterpreterResult ret = sql.interpret("select * from gr");
		assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
	}

	@Test
	public void test_null_value_in_row() {
		repl.interpret("import org.apache.spark.sql._");
		repl.interpret("def toInt(s:String): Any = {try { s.trim().toInt} catch {case e:Exception => null}}");
		repl.interpret("val schema = StructType(Seq(StructField(\"name\", StringType, false),StructField(\"age\" , IntegerType, true),StructField(\"other\" , StringType, false)))");
		repl.interpret("val csv = sc.parallelize(Seq((\"jobs, 51, apple\"), (\"gates, , microsoft\")))");
		repl.interpret("val raw = csv.map(_.split(\",\")).map(p => Row(p(0),toInt(p(1)),p(2)))");
		repl.interpret("val people = z.sqlContext.applySchema(raw, schema)");
		repl.interpret("people.registerTempTable(\"people\")");

		InterpreterResult ret = sql.interpret("select name, age from people where name = 'gates'");
		System.err.println("RET=" + ret.message());
		assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
		assertEquals(Type.TABLE, ret.type());
		assertEquals("name\tage\ngates\tnull\n", ret.message());
	}
}
