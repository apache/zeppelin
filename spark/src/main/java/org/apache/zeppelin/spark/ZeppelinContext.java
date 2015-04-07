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
import java.util.LinkedList;
import java.util.List;

import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.hive.HiveContext;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectWatcher;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input.ParamOption;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
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

  private void restartInterpreter() {
  }

  public InterpreterContext getInterpreterContext() {
    return interpreterContext;
  }

  public void setInterpreterContext(InterpreterContext interpreterContext) {
    this.interpreterContext = interpreterContext;
  }

  /**
   * Run paragraph by id
   * @param id
   */
  public void run(String id) {
    if (id.equals(interpreterContext.getParagraphId())) {
      throw new InterpreterException("Can not run current Paragraph");
    }

    for (InterpreterContextRunner r : interpreterContext.getRunners()) {
      if (id.equals(r.getParagraphId())) {
        r.run();
        return;
      }
    }

    throw new InterpreterException("Paragraph " + id + " not found");
  }

  /**
   * Run paragraph at index
   * @param idx index starting from 0
   */
  public void run(int idx) {
    if (idx >= interpreterContext.getRunners().size()) {
      throw new InterpreterException("Index out of bound");
    }

    InterpreterContextRunner runner = interpreterContext.getRunners().get(idx);
    if (runner.getParagraphId().equals(interpreterContext.getParagraphId())) {
      throw new InterpreterException("Can not run current Paragraph");
    }

    runner.run();
  }

  /**
   * Run paragraphs
   * @param paragraphIdOrIdxs list of paragraph id or idx
   */
  public void run(List<Object> paragraphIdOrIdx) {
    for (Object idOrIdx : paragraphIdOrIdx) {
      if (idOrIdx instanceof String) {
        String id = (String) idOrIdx;
        run(id);
      } else if (idOrIdx instanceof Integer) {
        Integer idx = (Integer) idOrIdx;
        run(idx);
      } else {
        throw new InterpreterException("Paragraph " + idOrIdx + " not found");
      }
    }
  }


  /**
   * Run all paragraphs. except this.
   */
  public void runAll() {
    for (InterpreterContextRunner r : interpreterContext.getRunners()) {
      if (r.getParagraphId().equals(interpreterContext.getParagraphId())) {
        // skip itself
        continue;
      }
      r.run();
    }
  }

  public List<String> listParagraphs() {
    List<String> paragraphs = new LinkedList<String>();

    for (InterpreterContextRunner r : interpreterContext.getRunners()) {
      paragraphs.add(r.getParagraphId());
    }

    return paragraphs;
  }



  public Object angular(String name) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    AngularObject ao = registry.get(name);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  public void angularBind(String name, Object o) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    if (registry.get(name) == null) {
      registry.add(name, o);
    } else {
      registry.get(name).set(o);
    }
  }

  public void angularBind(String name, Object o, AngularObjectWatcher w) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    if (registry.get(name) == null) {
      registry.add(name, o);
    } else {
      registry.get(name).set(o);
    }
    angularWatch(name, w);
  }

  public void angularWatch(String name, AngularObjectWatcher w) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    if (registry.get(name) != null) {
      registry.get(name).addWatcher(w);
    }
  }

  public void angularUnwatch(String name, AngularObjectWatcher w) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    if (registry.get(name) != null) {
      registry.get(name).removeWatcher(w);
    }
  }

  public void angularUnwatch(String name) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    if (registry.get(name) != null) {
      registry.get(name).clearAllWatchers();
    }
  }

  public void angularUnbind(String name) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    registry.remove(name);
  }
}
