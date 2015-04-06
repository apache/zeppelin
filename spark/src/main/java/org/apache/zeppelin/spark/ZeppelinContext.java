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

import static scala.collection.JavaConversions.asJavaCollection;
import static scala.collection.JavaConversions.asJavaIterable;
import static scala.collection.JavaConversions.collectionAsScalaIterable;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.hive.HiveContext;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input.ParamOption;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.spark.dep.DependencyResolver;

import scala.Tuple2;
import scala.collection.Iterable;

/**
 * Spark context for zeppelin.
 *
 * @author Leemoonsoo
 *
 */
public class ZeppelinContext extends HashMap<String, Object> {
  private DependencyResolver dep;
  private PrintStream out;
  private InterpreterContext interpreterContext;

  public ZeppelinContext(SparkContext sc, SQLContext sql,
      InterpreterContext interpreterContext,
      DependencyResolver dep, PrintStream printStream) {
    this.sc = sc;
    this.sqlContext = sql;
    this.interpreterContext = interpreterContext;
    this.dep = dep;
    this.out = printStream;
  }

  public SparkContext sc;
  public SQLContext sqlContext;
  public HiveContext hiveContext;
  private GUI gui;

  /* spark-1.3
  public SchemaRDD sql(String sql) {
    return sqlContext.sql(sql);
  }
  */

  /**
   * Load dependency for interpreter and runtime (driver).
   * And distribute them to spark cluster (sc.add())
   *
   * @param artifact "group:artifact:version" or file path like "/somepath/your.jar"
   * @return
   * @throws Exception
   */
  public Iterable<String> load(String artifact) throws Exception {
    return collectionAsScalaIterable(dep.load(artifact, true));
  }

  /**
   * Load dependency and it's transitive dependencies for interpreter and runtime (driver).
   * And distribute them to spark cluster (sc.add())
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @param excludes exclusion list of transitive dependency. list of "groupId:artifactId" string.
   * @return
   * @throws Exception
   */
  public Iterable<String> load(String artifact, scala.collection.Iterable<String> excludes)
      throws Exception {
    return collectionAsScalaIterable(
        dep.load(artifact,
        asJavaCollection(excludes),
        true));
  }

  /**
   * Load dependency and it's transitive dependencies for interpreter and runtime (driver).
   * And distribute them to spark cluster (sc.add())
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @param excludes exclusion list of transitive dependency. list of "groupId:artifactId" string.
   * @return
   * @throws Exception
   */
  public Iterable<String> load(String artifact, Collection<String> excludes) throws Exception {
    return collectionAsScalaIterable(dep.load(artifact, excludes, true));
  }

  /**
   * Load dependency for interpreter and runtime, and then add to sparkContext.
   * But not adding them to spark cluster
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @return
   * @throws Exception
   */
  public Iterable<String> loadLocal(String artifact) throws Exception {
    return collectionAsScalaIterable(dep.load(artifact, false));
  }


  /**
   * Load dependency and it's transitive dependencies and then add to sparkContext.
   * But not adding them to spark cluster
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @param excludes exclusion list of transitive dependency. list of "groupId:artifactId" string.
   * @return
   * @throws Exception
   */
  public Iterable<String> loadLocal(String artifact,
      scala.collection.Iterable<String> excludes) throws Exception {
    return collectionAsScalaIterable(dep.load(artifact,
        asJavaCollection(excludes), false));
  }

  /**
   * Load dependency and it's transitive dependencies and then add to sparkContext.
   * But not adding them to spark cluster
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @param excludes exclusion list of transitive dependency. list of "groupId:artifactId" string.
   * @return
   * @throws Exception
   */
  public Iterable<String> loadLocal(String artifact, Collection<String> excludes)
      throws Exception {
    return collectionAsScalaIterable(dep.load(artifact, excludes, false));
  }


  /**
   * Add maven repository
   *
   * @param id id of repository ex) oss, local, snapshot
   * @param url url of repository. supported protocol : file, http, https
   */
  public void addRepo(String id, String url) {
    addRepo(id, url, false);
  }

  /**
   * Add maven repository
   *
   * @param id id of repository
   * @param url url of repository. supported protocol : file, http, https
   * @param snapshot true if it is snapshot repository
   */
  public void addRepo(String id, String url, boolean snapshot) {
    dep.addRepo(id, url, snapshot);
  }

  /**
   * Remove maven repository by id
   * @param id id of repository
   */
  public void removeRepo(String id){
    dep.delRepo(id);
  }

  /**
   * Load dependency only interpreter.
   *
   * @param name
   * @return
   */

  public Object input(String name) {
    return input(name, "");
  }

  public Object input(String name, Object defaultValue) {
    return gui.input(name, defaultValue);
  }

  public Object select(String name, scala.collection.Iterable<Tuple2<Object, String>> options) {
    return select(name, "", options);
  }

  public Object select(String name, Object defaultValue,
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    int n = options.size();
    ParamOption[] paramOptions = new ParamOption[n];
    Iterator<Tuple2<Object, String>> it = asJavaIterable(options).iterator();

    int i = 0;
    while (it.hasNext()) {
      Tuple2<Object, String> valueAndDisplayValue = it.next();
      paramOptions[i++] = new ParamOption(valueAndDisplayValue._1(), valueAndDisplayValue._2());
    }

    return gui.select(name, "", paramOptions);
  }

  public void setGui(GUI o) {
    this.gui = o;
  }

  public void run(String lines) {
    /*
    String intpName = Paragraph.getRequiredReplName(lines);
    String scriptBody = Paragraph.getScriptBody(lines);
    Interpreter intp = interpreterContext.getParagraph().getRepl(intpName);
    InterpreterResult ret = intp.interpret(scriptBody, interpreterContext);
    if (ret.code() == InterpreterResult.Code.SUCCESS) {
      out.println("%" + ret.type().toString().toLowerCase() + " " + ret.message());
    } else if (ret.code() == InterpreterResult.Code.ERROR) {
      out.println("Error: " + ret.message());
    } else if (ret.code() == InterpreterResult.Code.INCOMPLETE) {
      out.println("Incomplete");
    } else {
      out.println("Unknown error");
    }
    */
    throw new RuntimeException("Missing implementation");
  }

  private void restartInterpreter() {
  }

  public InterpreterContext getInterpreterContext() {
    return interpreterContext;
  }

  public void setInterpreterContext(InterpreterContext interpreterContext) {
    this.interpreterContext = interpreterContext;
  }

}
