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

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteEventClientWrapper;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.resource.WellKnownResourceName;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SparkInterpreterTest {

  @ClassRule
  public static TemporaryFolder tmpDir = new TemporaryFolder();

  static SparkInterpreter repl;
  static InterpreterGroup intpGroup;
  static InterpreterContext context;
  static Logger LOGGER = LoggerFactory.getLogger(SparkInterpreterTest.class);
  static Map<String, Map<String, String>> paraIdToInfosMap =
      new HashMap<>();

  /**
   * Get spark version number as a numerical value.
   * eg. 1.1.x => 11, 1.2.x => 12, 1.3.x => 13 ...
   */
  public static int getSparkVersionNumber(SparkInterpreter repl) {
    if (repl == null) {
      return 0;
    }

    String[] split = repl.getSparkContext().version().split("\\.");
    int version = Integer.parseInt(split[0]) * 10 + Integer.parseInt(split[1]);
    return version;
  }

  public static Properties getSparkTestProperties(TemporaryFolder tmpDir) throws IOException {
    Properties p = new Properties();
    p.setProperty("master", "local[*]");
    p.setProperty("spark.app.name", "Zeppelin Test");
    p.setProperty("zeppelin.spark.useHiveContext", "true");
    p.setProperty("zeppelin.spark.maxResult", "1000");
    p.setProperty("zeppelin.spark.importImplicit", "true");
    p.setProperty("zeppelin.dep.localrepo", tmpDir.newFolder().getAbsolutePath());
    p.setProperty("zeppelin.spark.property_1", "value_1");
    return p;
  }

  @BeforeClass
  public static void setUp() throws Exception {
    intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());
    repl = new SparkInterpreter(getSparkTestProperties(tmpDir));
    repl.setInterpreterGroup(intpGroup);
    intpGroup.get("note").add(repl);
    repl.open();

    final RemoteEventClientWrapper remoteEventClientWrapper = new RemoteEventClientWrapper() {

      @Override
      public void onParaInfosReceived(String noteId, String paragraphId,
          Map<String, String> infos) {
        if (infos != null) {
          paraIdToInfosMap.put(paragraphId, infos);
        }
      }

      @Override
      public void onMetaInfosReceived(Map<String, String> infos) {
      }
    };
    context = new InterpreterContext("note", "id", null, "title", "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LocalResourcePool("id"),
        new LinkedList<InterpreterContextRunner>(),
        new InterpreterOutput(null)) {
        
        @Override
        public RemoteEventClientWrapper getClient() {
          return remoteEventClientWrapper;
        }
    };
    // The first para interpretdr will set the Eventclient wrapper
    //SparkInterpreter.interpret(String, InterpreterContext) ->
    //SparkInterpreter.populateSparkWebUrl(InterpreterContext) ->
    //ZeppelinContext.setEventClient(RemoteEventClientWrapper)
    //running a dummy to ensure that we dont have any race conditions among tests
    repl.interpret("sc", context);
  }

  @AfterClass
  public static void tearDown() {
    repl.close();
  }

  @Test
  public void testBasicIntp() {
    assertEquals(InterpreterResult.Code.SUCCESS,
        repl.interpret("val a = 1\nval b = 2", context).code());

    // when interpret incomplete expression
    InterpreterResult incomplete = repl.interpret("val a = \"\"\"", context);
    assertEquals(InterpreterResult.Code.INCOMPLETE, incomplete.code());
    assertTrue(incomplete.message().get(0).getData().length() > 0); // expecting some error
                                                   // message

    /*
     * assertEquals(1, repl.getValue("a")); assertEquals(2, repl.getValue("b"));
     * repl.interpret("val ver = sc.version");
     * assertNotNull(repl.getValue("ver")); assertEquals("HELLO\n",
     * repl.interpret("println(\"HELLO\")").message());
     */
  }

  @Test
  public void testNonStandardSparkProperties() throws IOException {
    // throw NoSuchElementException if no such property is found
    InterpreterResult result = repl.interpret("sc.getConf.get(\"property_1\")", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }

  @Test
  public void testNextLineInvocation() {
    assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret("\"123\"\n.toInt", context).code());
  }

  @Test
  public void testNextLineComments() {
    assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret("\"123\"\n/*comment here\n*/.toInt", context).code());
  }

  @Test
  public void testNextLineCompanionObject() {
    String code = "class Counter {\nvar value: Long = 0\n}\n // comment\n\n object Counter {\n def apply(x: Long) = new Counter()\n}";
    assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret(code, context).code());
  }

  @Test
  public void testEndWithComment() {
    assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret("val c=1\n//comment", context).code());
  }

  @Test
  public void testListener() {
    SparkContext sc = repl.getSparkContext();
    assertNotNull(SparkInterpreter.setupListeners(sc));
  }

  @Test
  public void testCreateDataFrame() {
    if (getSparkVersionNumber(repl) >= 13) {
      repl.interpret("case class Person(name:String, age:Int)\n", context);
      repl.interpret("val people = sc.parallelize(Seq(Person(\"moon\", 33), Person(\"jobs\", 51), Person(\"gates\", 51), Person(\"park\", 34)))\n", context);
      repl.interpret("people.toDF.count", context);
      assertEquals(new Long(4), context.getResourcePool().get(
          context.getNoteId(),
          context.getParagraphId(),
          WellKnownResourceName.ZeppelinReplResult.toString()).get());
    }
  }

  @Test
  public void testZShow() {
    String code = "";
    repl.interpret("case class Person(name:String, age:Int)\n", context);
    repl.interpret("val people = sc.parallelize(Seq(Person(\"moon\", 33), Person(\"jobs\", 51), Person(\"gates\", 51), Person(\"park\", 34)))\n", context);
    if (getSparkVersionNumber(repl) < 13) {
      repl.interpret("people.registerTempTable(\"people\")", context);
      code = "z.show(sqlc.sql(\"select * from people\"))";
    } else {
      code = "z.show(people.toDF)";
    }
      assertEquals(Code.SUCCESS, repl.interpret(code, context).code());
  }

  @Test
  public void testSparkSql() throws IOException, InterpreterException {
    repl.interpret("case class Person(name:String, age:Int)\n", context);
    repl.interpret("val people = sc.parallelize(Seq(Person(\"moon\", 33), Person(\"jobs\", 51), Person(\"gates\", 51), Person(\"park\", 34)))\n", context);
    assertEquals(Code.SUCCESS, repl.interpret("people.take(3)", context).code());


    if (getSparkVersionNumber(repl) <= 11) { // spark 1.2 or later does not allow create multiple
      // SparkContext in the same jvm by default.
      // create new interpreter
      SparkInterpreter repl2 = new SparkInterpreter(getSparkTestProperties(tmpDir));
      repl2.setInterpreterGroup(intpGroup);
      intpGroup.get("note").add(repl2);
      repl2.open();

      repl2.interpret("case class Man(name:String, age:Int)", context);
      repl2.interpret("val man = sc.parallelize(Seq(Man(\"moon\", 33), Man(\"jobs\", 51), Man(\"gates\", 51), Man(\"park\", 34)))", context);
      assertEquals(Code.SUCCESS, repl2.interpret("man.take(3)", context).code());
      repl2.close();
    }
  }

  @Test
  public void testReferencingUndefinedVal() {
    InterpreterResult result = repl.interpret("def category(min: Int) = {"
        + "    if (0 <= value) \"error\"" + "}", context);
    assertEquals(Code.ERROR, result.code());
  }

  @Test
  public void emptyConfigurationVariablesOnlyForNonSparkProperties() {
    Properties intpProperty = repl.getProperties();
    SparkConf sparkConf = repl.getSparkContext().getConf();
    for (Object oKey : intpProperty.keySet()) {
      String key = (String) oKey;
      String value = (String) intpProperty.get(key);
      LOGGER.debug(String.format("[%s]: [%s]", key, value));
      if (key.startsWith("spark.") && value.isEmpty()) {
        assertTrue(String.format("configuration starting from 'spark.' should not be empty. [%s]", key), !sparkConf.contains(key) || !sparkConf.get(key).isEmpty());
      }
    }
  }

  @Test
  public void shareSingleSparkContext() throws InterruptedException, IOException, InterpreterException {
    // create another SparkInterpreter
    SparkInterpreter repl2 = new SparkInterpreter(getSparkTestProperties(tmpDir));
    repl2.setInterpreterGroup(intpGroup);
    intpGroup.get("note").add(repl2);
    repl2.open();

    assertEquals(Code.SUCCESS,
        repl.interpret("print(sc.parallelize(1 to 10).count())", context).code());
    assertEquals(Code.SUCCESS,
        repl2.interpret("print(sc.parallelize(1 to 10).count())", context).code());

    repl2.close();
  }

  @Test
  public void testEnableImplicitImport() throws IOException, InterpreterException {
    if (getSparkVersionNumber(repl) >= 13) {
      // Set option of importing implicits to "true", and initialize new Spark repl
      Properties p = getSparkTestProperties(tmpDir);
      p.setProperty("zeppelin.spark.importImplicit", "true");
      SparkInterpreter repl2 = new SparkInterpreter(p);
      repl2.setInterpreterGroup(intpGroup);
      intpGroup.get("note").add(repl2);

      repl2.open();
      String ddl = "val df = Seq((1, true), (2, false)).toDF(\"num\", \"bool\")";
      assertEquals(Code.SUCCESS, repl2.interpret(ddl, context).code());
      repl2.close();
    }
  }

  @Test
  public void testDisableImplicitImport() throws IOException, InterpreterException {
    if (getSparkVersionNumber(repl) >= 13) {
      // Set option of importing implicits to "false", and initialize new Spark repl
      // this test should return error status when creating DataFrame from sequence
      Properties p = getSparkTestProperties(tmpDir);
      p.setProperty("zeppelin.spark.importImplicit", "false");
      SparkInterpreter repl2 = new SparkInterpreter(p);
      repl2.setInterpreterGroup(intpGroup);
      intpGroup.get("note").add(repl2);

      repl2.open();
      String ddl = "val df = Seq((1, true), (2, false)).toDF(\"num\", \"bool\")";
      assertEquals(Code.ERROR, repl2.interpret(ddl, context).code());
      repl2.close();
    }
  }

  @Test
  public void testCompletion() {
    List<InterpreterCompletion> completions = repl.completion("sc.", "sc.".length(), null);
    assertTrue(completions.size() > 0);
  }

  @Test
  public void testMultilineCompletion() {
    String buf = "val x = 1\nsc.";
	List<InterpreterCompletion> completions = repl.completion(buf, buf.length(), null);
    assertTrue(completions.size() > 0);
  }

  @Test
  public void testMultilineCompletionNewVar() {
    Assume.assumeFalse("this feature does not work with scala 2.10", Utils.isScala2_10());
    Assume.assumeTrue("This feature does not work with scala < 2.11.8", Utils.isCompilerAboveScala2_11_7());
    String buf = "val x = sc\nx.";
	  List<InterpreterCompletion> completions = repl.completion(buf, buf.length(), null);
    assertTrue(completions.size() > 0);
  }

  @Test
  public void testParagraphUrls() {
    String paraId = "test_para_job_url";
    InterpreterContext intpCtx = new InterpreterContext("note", paraId, null, "title", "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LocalResourcePool("id"),
        new LinkedList<InterpreterContextRunner>(),
        new InterpreterOutput(null));
    repl.interpret("sc.parallelize(1 to 10).map(x => {x}).collect", intpCtx);
    Map<String, String> paraInfos = paraIdToInfosMap.get(intpCtx.getParagraphId());
    String jobUrl = null;
    if (paraInfos != null) {
      jobUrl = paraInfos.get("jobUrl");
    }
    String sparkUIUrl = repl.getSparkUIUrl();
    assertNotNull(jobUrl);
    assertTrue(jobUrl.startsWith(sparkUIUrl + "/jobs/job/?id="));

  }
}
