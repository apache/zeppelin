package org.apache.zeppelin.spark;

import org.apache.spark.api.java.JavaSparkContext;
import java.util.Properties;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.kotlin.KotlinInterpreter;

public class KotlinSparkInterpreter extends Interpreter {
  private KotlinInterpreter interpreter;
  private SparkInterpreter sparkInterpreter;

  public KotlinSparkInterpreter(Properties properties) {
    super(properties);
    interpreter = new KotlinInterpreter(properties);
  }

  @Override
  public void open() throws InterpreterException {
    sparkInterpreter =
        getInterpreterInTheSameSessionByClassName(SparkInterpreter.class);
    Object spark = sparkInterpreter.getSparkSession();
    JavaSparkContext sc = sparkInterpreter.getJavaSparkContext();
    interpreter.setExecutionContext(new KotlinSparkExecutionContext(spark, sc));
    interpreter.open();
  }

  @Override
  public void close() throws InterpreterException {
    interpreter.close();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    return interpreter.interpret(st, context);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    interpreter.cancel(context);
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return interpreter.getFormType();
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return interpreter.getProgress(context);
  }
}
