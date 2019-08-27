/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.spark;

import static org.apache.zeppelin.spark.Utils.buildJobDesc;
import static org.apache.zeppelin.spark.Utils.buildJobGroupId;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import scala.Console;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.kotlin.KotlinInterpreter;
import org.apache.zeppelin.spark.kotlin.KotlinZeppelinBindings;
import org.apache.zeppelin.spark.kotlin.SparkKotlinReceiver;

public class KotlinSparkInterpreter extends Interpreter {
  private static Logger logger = LoggerFactory.getLogger(KotlinSparkInterpreter.class);

  private KotlinInterpreter interpreter;
  private SparkInterpreter sparkInterpreter;
  private BaseZeppelinContext z;
  private JavaSparkContext jsc;

  public KotlinSparkInterpreter(Properties properties) {
    super(properties);
    logger.debug("Creating KotlinSparkInterpreter");
    interpreter = new KotlinInterpreter(properties);
  }

  @Override
  public void open() throws InterpreterException {
    sparkInterpreter =
        getInterpreterInTheSameSessionByClassName(SparkInterpreter.class);
    jsc = sparkInterpreter.getJavaSparkContext();
    z = sparkInterpreter.getZeppelinContext();

    SparkKotlinReceiver ctx = new SparkKotlinReceiver(
        (SparkSession) sparkInterpreter.getSparkSession(),
        jsc,
        sparkInterpreter.getSQLContext(),
        z);

    List<String> classpath = sparkClasspath();

    // TODO(dk) fix NPE in tests
    String outputDir;
    try {
      outputDir = jsc.getConf().getOption("spark.repl.class.outputDir").getOrElse(null);
    } catch (NullPointerException e) {
      outputDir = null;
    }

    interpreter.getBuilder()
        .executionContext(ctx)
        .classPath(classpath)
        .outputDir(outputDir)
        .codeOnLoad(KotlinZeppelinBindings.Z_SELECT_KOTLIN_SYNTAX)
        .codeOnLoad(KotlinZeppelinBindings.SPARK_UDF_IMPORTS);

    interpreter.open();
  }

  @Override
  public void close() throws InterpreterException {
    interpreter.close();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {

    z.setInterpreterContext(context);
    z.setGui(context.getGui());
    z.setNoteGui(context.getNoteGui());
    InterpreterContext.set(context);

    jsc.setJobGroup(buildJobGroupId(context), buildJobDesc(context), false);
    jsc.setLocalProperty("spark.scheduler.pool", context.getLocalProperties().get("pool"));

    InterpreterOutput out = context.out;
    PrintStream scalaOut = Console.out();
    PrintStream newOut = (out != null) ? new PrintStream(out) : null;

    Console.setOut(newOut);
    InterpreterResult result = interpreter.interpret(st, context);
    Console.setOut(scalaOut);

    return result;
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    jsc.cancelJobGroup(buildJobGroupId(context));
    interpreter.cancel(context);
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return interpreter.getFormType();
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return sparkInterpreter.getProgress(context);
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) throws InterpreterException {
    return interpreter.completion(buf, cursor, interpreterContext);
  }

  private List<String> sparkClasspath() {
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
        .collect(Collectors.toList());
  }
}