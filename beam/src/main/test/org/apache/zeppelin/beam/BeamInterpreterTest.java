package org.apache.zeppelin.beam;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

import org.apache.zeppelin.beam.BeamInterpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class BeamInterpreterTest {

	private static BeamInterpreter beam;
	private static InterpreterContext context;

	@BeforeClass
	public static void setUp() {
		Properties p = new Properties();
		beam = new BeamInterpreter(p);
		beam.open();
		context = new InterpreterContext(null, null, null, null, null, null,
				null, null, null, null, null);
	}

	@AfterClass
	public static void tearDown() {
		beam.close();
	}

	@Test
	public void testStaticRepl() {

		StringWriter writer = new StringWriter();
		PrintWriter out = new PrintWriter(writer);
		out.println("public class HelloWorld {");
		out.println("  public static void main(String args[]) {");
		out.println("    System.out.println(\"This is in another java file\");");
		out.println("  }");
		out.println("}");
		out.close();

		InterpreterResult res = beam.interpret(writer.toString(), context);

		assertEquals(InterpreterResult.Code.SUCCESS, res.code());
	}
	
	@Test
	public void testStaticReplWithoutMain() {

		StringBuffer sourceCode = new StringBuffer();
		sourceCode.append("package org.mdkt;\n");
		sourceCode.append("public class HelloClass {\n");
		sourceCode.append("   public String hello() { return \"hello\"; }");
		sourceCode.append("}");
		InterpreterResult res = beam.interpret(sourceCode.toString(), context);
		assertEquals(InterpreterResult.Code.ERROR, res.code());
	}
	
	@Test
	public void testStaticReplWithSyntaxError() {

		StringWriter writer = new StringWriter();
		PrintWriter out = new PrintWriter(writer);
		out.println("public class HelloWorld {");
		out.println("  public static void main(String args[]) {");
		out.println("    System.out.prin(\"This is in another java file\");");
		out.println("  }");
		out.println("}");
		out.close();
		InterpreterResult res = beam.interpret(writer.toString(), context);

		assertEquals(InterpreterResult.Code.ERROR, res.code());
	}

}
