package org.apache.zeppelin.spark;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.kotlin.KotlinInterpreter;


public class KotlinSparkInterpreter extends Interpreter {
  private static Logger logger = LoggerFactory.getLogger(KotlinSparkInterpreter.class);

  private KotlinInterpreter interpreter;
  private SparkInterpreter sparkInterpreter;

  public KotlinSparkInterpreter(Properties properties) {
    super(properties);
    logger.debug("Creating KotlinSparkInterpreter");
    interpreter = new KotlinInterpreter(properties);
  }


  private String sparkClasspath() {
    String sparkJars = System.getProperty("spark.jars");
    Pattern isKotlinJar = Pattern.compile("/kotlin-(runtime|stdlib|compiler|reflect)(-.*)?\\.jar");

    Stream<File> addedJars = Arrays.stream(Utils.resolveURIs(sparkJars).split(","))
        .filter(s -> !s.trim().equals(""))
        .filter(s -> !isKotlinJar.matcher(s).find())
        .map(s -> {
          int p = s.indexOf(':');
          return new File(s.substring(p + 1));
        });

    Stream<File> systemJars = Arrays.stream(
        System.getProperty("java.class.path").split(File.pathSeparator))
        .map(File::new);

    return Stream.concat(addedJars, systemJars)
        .map(file -> {
          try {
            return file.getCanonicalPath();
          } catch (IOException e) {
            return "";
          }
        })
        .collect(Collectors.joining(File.pathSeparator));
  }


  @Override
  public void open() throws InterpreterException {
    sparkInterpreter =
        getInterpreterInTheSameSessionByClassName(SparkInterpreter.class);

    Object spark = sparkInterpreter.getSparkSession();
    JavaSparkContext sc = sparkInterpreter.getJavaSparkContext();

    logger.debug("setting execution ctx now");
    interpreter.setExecutionContext(new KotlinSparkExecutionContext(spark, sc));

    String cp = sparkClasspath();
    logger.debug(cp);
    List<String> compilerOptions = Arrays.asList("-classpath", cp);

    logger.debug("setting compiler options now");
    interpreter.setCompilerOptions(compilerOptions);

    logger.debug("opening inner intp now");
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
