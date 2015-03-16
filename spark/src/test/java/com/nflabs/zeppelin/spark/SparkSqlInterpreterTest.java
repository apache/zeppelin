package com.nflabs.zeppelin.spark;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.display.GUI;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Type;

public class SparkSqlInterpreterTest {

	private SparkSqlInterpreter sql;
  private SparkInterpreter repl;
  private InterpreterContext context;

	@Before
	public void setUp() throws Exception {
		Properties p = new Properties();

		if (repl == null) {

		  if (SparkInterpreterTest.repl == null) {
		    repl = new SparkInterpreter(p);
		    repl.open();
		    SparkInterpreterTest.repl = repl;
		  } else {
		    repl = SparkInterpreterTest.repl;
		  }

  		sql = new SparkSqlInterpreter(p);

  		InterpreterGroup intpGroup = new InterpreterGroup();
		  intpGroup.add(repl);
		  intpGroup.add(sql);
		  sql.setInterpreterGroup(intpGroup);
		  sql.open();
		}
		context = new InterpreterContext("id", "title", "text", new HashMap<String, Object>(), new GUI());
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		repl.interpret("case class Test(name:String, age:Int)", context);
		repl.interpret("val test = sc.parallelize(Seq(Test(\"moon\", 33), Test(\"jobs\", 51), Test(\"gates\", 51), Test(\"park\", 34)))", context);
		repl.interpret("test.registerAsTable(\"test\")", context);

		InterpreterResult ret = sql.interpret("select name, age from test where age < 40", context);
		assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
		assertEquals(Type.TABLE, ret.type());
		assertEquals("name\tage\nmoon\t33\npark\t34\n", ret.message());

		assertEquals(InterpreterResult.Code.ERROR, sql.interpret("select wrong syntax", context).code());
		assertEquals(InterpreterResult.Code.SUCCESS, sql.interpret("select case when name==\"aa\" then name else name end from people", context).code());
	}

	@Test
	public void testStruct(){
		repl.interpret("case class Person(name:String, age:Int)", context);
		repl.interpret("case class People(group:String, person:Person)", context);
		repl.interpret("val gr = sc.parallelize(Seq(People(\"g1\", Person(\"moon\",33)), People(\"g2\", Person(\"sun\",11))))", context);
		repl.interpret("gr.registerAsTable(\"gr\")", context);
		InterpreterResult ret = sql.interpret("select * from gr", context);
		assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
	}

	@Test
	public void test_null_value_in_row() {
		repl.interpret("import org.apache.spark.sql._", context);
		repl.interpret("def toInt(s:String): Any = {try { s.trim().toInt} catch {case e:Exception => null}}", context);
		repl.interpret("val schema = StructType(Seq(StructField(\"name\", StringType, false),StructField(\"age\" , IntegerType, true),StructField(\"other\" , StringType, false)))", context);
		repl.interpret("val csv = sc.parallelize(Seq((\"jobs, 51, apple\"), (\"gates, , microsoft\")))", context);
		repl.interpret("val raw = csv.map(_.split(\",\")).map(p => Row(p(0),toInt(p(1)),p(2)))", context);
		repl.interpret("val people = z.sqlContext.applySchema(raw, schema)", context);
		repl.interpret("people.registerTempTable(\"people\")", context);

		InterpreterResult ret = sql.interpret("select name, age from people where name = 'gates'", context);
		System.err.println("RET=" + ret.message());
		assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
		assertEquals(Type.TABLE, ret.type());
		assertEquals("name\tage\ngates\tnull\n", ret.message());
	}
}
