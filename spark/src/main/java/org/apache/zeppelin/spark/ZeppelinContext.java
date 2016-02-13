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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SQLContext.QueryExecution;
import org.apache.spark.sql.catalyst.expressions.Attribute;
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
import scala.Unit;
import scala.collection.Iterable;

/**
 * Spark context for zeppelin.
 */
public class ZeppelinContext extends HashMap<String, Object> {
  private DependencyResolver dep;
  private PrintStream out;
  private InterpreterContext interpreterContext;
  private int maxResult;

  public ZeppelinContext(SparkContext sc, SQLContext sql,
      InterpreterContext interpreterContext,
      DependencyResolver dep, PrintStream printStream,
      int maxResult) {
    this.sc = sc;
    this.sqlContext = sql;
    this.interpreterContext = interpreterContext;
    this.dep = dep;
    this.out = printStream;
    this.maxResult = maxResult;
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

  public void setMaxResult(int maxResult) {
    this.maxResult = maxResult;
  }

  /**
   * show DataFrame or SchemaRDD
   * @param o DataFrame or SchemaRDD object
   */
  public void show(Object o) {
    show(o, maxResult);
  }

  /**
   * show DataFrame or SchemaRDD
   * @param o DataFrame or SchemaRDD object
   * @param maxResult maximum number of rows to display
   */
  public void show(Object o, int maxResult) {
    Class cls = null;
    try {
      cls = this.getClass().forName("org.apache.spark.sql.DataFrame");
    } catch (ClassNotFoundException e) {
    }

    if (cls == null) {
      try {
        cls = this.getClass().forName("org.apache.spark.sql.SchemaRDD");
      } catch (ClassNotFoundException e) {
      }
    }

    if (cls == null) {
      throw new InterpreterException("Can not road DataFrame/SchemaRDD class");
    }

    if (cls.isInstance(o)) {
      out.print(showDF(sc, interpreterContext, o, maxResult));
    } else {
      out.print(o.toString());
    }
  }

  public static String showDF(ZeppelinContext z, Object df) {
    return showDF(z.sc, z.interpreterContext, df, z.maxResult);
  }

  public static String showDF(SparkContext sc,
      InterpreterContext interpreterContext,
      Object df, int maxResult) {
    Object[] rows = null;
    Method take;
    String jobGroup = "zeppelin-" + interpreterContext.getParagraphId();
    sc.setJobGroup(jobGroup, "Zeppelin", false);

    try {
      take = df.getClass().getMethod("take", int.class);
      rows = (Object[]) take.invoke(df, maxResult + 1);
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException | ClassCastException e) {
      sc.clearJobGroup();
      throw new InterpreterException(e);
    }

    List<Attribute> columns = null;
    // get field names
    try {
      // Use reflection because of classname returned by queryExecution changes from
      // Spark <1.5.2 org.apache.spark.sql.SQLContext$QueryExecution
      // Spark 1.6.0> org.apache.spark.sql.hive.HiveContext$QueryExecution
      Object qe = df.getClass().getMethod("queryExecution").invoke(df);
      Object a = qe.getClass().getMethod("analyzed").invoke(qe);
      scala.collection.Seq seq = (scala.collection.Seq) a.getClass().getMethod("output").invoke(a);

      columns = (List<Attribute>) scala.collection.JavaConverters.seqAsJavaListConverter(seq)
                                                                 .asJava();
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      throw new InterpreterException(e);
    }

    String msg = null;
    for (Attribute col : columns) {
      if (msg == null) {
        msg = col.name();
      } else {
        msg += "\t" + col.name();
      }
    }

    msg += "\n";

    // ArrayType, BinaryType, BooleanType, ByteType, DecimalType, DoubleType, DynamicType,
    // FloatType, FractionalType, IntegerType, IntegralType, LongType, MapType, NativeType,
    // NullType, NumericType, ShortType, StringType, StructType

    try {
      for (int r = 0; r < maxResult && r < rows.length; r++) {
        Object row = rows[r];
        Method isNullAt = row.getClass().getMethod("isNullAt", int.class);
        Method apply = row.getClass().getMethod("apply", int.class);

        for (int i = 0; i < columns.size(); i++) {
          if (!(Boolean) isNullAt.invoke(row, i)) {
            msg += apply.invoke(row, i).toString();
          } else {
            msg += "null";
          }
          if (i != columns.size() - 1) {
            msg += "\t";
          }
        }
        msg += "\n";
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      throw new InterpreterException(e);
    }

    if (rows.length > maxResult) {
      msg += "\n<font color=red>Results are limited by " + maxResult + ".</font>";
    }
    sc.clearJobGroup();
    return "%table " + msg;
  }

  /**
   * Run paragraph by id
   * @param id
   */
  public void run(String id) {
    run(id, interpreterContext);
  }

  /**
   * Run paragraph by id
   * @param id
   * @param context
   */
  public void run(String id, InterpreterContext context) {
    if (id.equals(context.getParagraphId())) {
      throw new InterpreterException("Can not run current Paragraph");
    }

    for (InterpreterContextRunner r : context.getRunners()) {
      if (id.equals(r.getParagraphId())) {
        r.run();
        return;
      }
    }

    throw new InterpreterException("Paragraph " + id + " not found");
  }

  /**
   * Run paragraph at idx
   * @param idx
   */
  public void run(int idx) {
    run(idx, interpreterContext);
  }

  /**
   * Run paragraph at index
   * @param idx index starting from 0
   * @param context interpreter context
   */
  public void run(int idx, InterpreterContext context) {
    if (idx >= context.getRunners().size()) {
      throw new InterpreterException("Index out of bound");
    }

    InterpreterContextRunner runner = context.getRunners().get(idx);
    if (runner.getParagraphId().equals(context.getParagraphId())) {
      throw new InterpreterException("Can not run current Paragraph");
    }

    runner.run();
  }

  public void run(List<Object> paragraphIdOrIdx) {
    run(paragraphIdOrIdx, interpreterContext);
  }

  /**
   * Run paragraphs
   * @param paragraphIdOrIdxs list of paragraph id or idx
   */
  public void run(List<Object> paragraphIdOrIdx, InterpreterContext context) {
    for (Object idOrIdx : paragraphIdOrIdx) {
      if (idOrIdx instanceof String) {
        String id = (String) idOrIdx;
        run(id, context);
      } else if (idOrIdx instanceof Integer) {
        Integer idx = (Integer) idOrIdx;
        run(idx, context);
      } else {
        throw new InterpreterException("Paragraph " + idOrIdx + " not found");
      }
    }
  }

  public void runAll() {
    runAll(interpreterContext);
  }

  /**
   * Run all paragraphs. except this.
   */
  public void runAll(InterpreterContext context) {
    for (InterpreterContextRunner r : context.getRunners()) {
      if (r.getParagraphId().equals(context.getParagraphId())) {
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


  private AngularObject getAngularObject(String name, InterpreterContext interpreterContext) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    String noteId = interpreterContext.getNoteId();
    // try get local object
    AngularObject ao = registry.get(name, interpreterContext.getNoteId());
    if (ao == null) {
      // then global object
      ao = registry.get(name, null);
    }
    return ao;
  }


  /**
   * Get angular object. Look up local registry first and then global registry
   * @param name variable name
   * @return value
   */
  public Object angular(String name) {
    AngularObject ao = getAngularObject(name, interpreterContext);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  /**
   * Get angular object. Look up global registry
   * @param name variable name
   * @return value
   */
  public Object angularGlobal(String name) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    AngularObject ao = registry.get(name, null);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  /**
   * Create angular variable in local registry and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  public void angularBind(String name, Object o) {
    angularBind(name, o, interpreterContext.getNoteId());
  }

  /**
   * Create angular variable in global registry and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  public void angularBindGlobal(String name, Object o) {
    angularBind(name, o, (String) null);
  }

  /**
   * Create angular variable in local registry and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  public void angularBind(String name, Object o, AngularObjectWatcher watcher) {
    angularBind(name, o, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Create angular variable in global registry and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  public void angularBindGlobal(String name, Object o, AngularObjectWatcher watcher) {
    angularBind(name, o, null, watcher);
  }

  /**
   * Add watcher into angular variable (local registry)
   * @param name name of the variable
   * @param watcher watcher
   */
  public void angularWatch(String name, AngularObjectWatcher watcher) {
    angularWatch(name, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Add watcher into angular variable (global registry)
   * @param name name of the variable
   * @param watcher watcher
   */
  public void angularWatchGlobal(String name, AngularObjectWatcher watcher) {
    angularWatch(name, null, watcher);
  }


  public void angularWatch(String name,
      final scala.Function2<Object, Object, Unit> func) {
    angularWatch(name, interpreterContext.getNoteId(), func);
  }

  public void angularWatchGlobal(String name,
      final scala.Function2<Object, Object, Unit> func) {
    angularWatch(name, null, func);
  }

  public void angularWatch(
      String name,
      final scala.Function3<Object, Object, InterpreterContext, Unit> func) {
    angularWatch(name, interpreterContext.getNoteId(), func);
  }

  public void angularWatchGlobal(
      String name,
      final scala.Function3<Object, Object, InterpreterContext, Unit> func) {
    angularWatch(name, null, func);
  }

  /**
   * Remove watcher from angular variable (local)
   * @param name
   * @param watcher
   */
  public void angularUnwatch(String name, AngularObjectWatcher watcher) {
    angularUnwatch(name, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Remove watcher from angular variable (global)
   * @param name
   * @param watcher
   */
  public void angularUnwatchGlobal(String name, AngularObjectWatcher watcher) {
    angularUnwatch(name, null, watcher);
  }


  /**
   * Remove all watchers for the angular variable (local)
   * @param name
   */
  public void angularUnwatch(String name) {
    angularUnwatch(name, interpreterContext.getNoteId());
  }

  /**
   * Remove all watchers for the angular variable (global)
   * @param name
   */
  public void angularUnwatchGlobal(String name) {
    angularUnwatch(name, (String) null);
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  public void angularUnbind(String name) {
    String noteId = interpreterContext.getNoteId();
    angularUnbind(name, noteId);
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  public void angularUnbindGlobal(String name) {
    angularUnbind(name, null);
  }

  /**
   * Create angular variable in local registry and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  private void angularBind(String name, Object o, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId) == null) {
      registry.add(name, o, noteId);
    } else {
      registry.get(name, noteId).set(o);
    }
  }

  /**
   * Create angular variable in local registry and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  private void angularBind(String name, Object o, String noteId, AngularObjectWatcher watcher) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId) == null) {
      registry.add(name, o, noteId);
    } else {
      registry.get(name, noteId).set(o);
    }
    angularWatch(name, watcher);
  }

  /**
   * Add watcher into angular binding variable
   * @param name name of the variable
   * @param watcher watcher
   */
  private void angularWatch(String name, String noteId, AngularObjectWatcher watcher) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId) != null) {
      registry.get(name, noteId).addWatcher(watcher);
    }
  }


  private void angularWatch(String name, String noteId,
      final scala.Function2<Object, Object, Unit> func) {
    AngularObjectWatcher w = new AngularObjectWatcher(getInterpreterContext()) {
      @Override
      public void watch(Object oldObject, Object newObject,
          InterpreterContext context) {
        func.apply(newObject, newObject);
      }
    };
    angularWatch(name, noteId, w);
  }

  private void angularWatch(
      String name,
      String noteId,
      final scala.Function3<Object, Object, InterpreterContext, Unit> func) {
    AngularObjectWatcher w = new AngularObjectWatcher(getInterpreterContext()) {
      @Override
      public void watch(Object oldObject, Object newObject,
          InterpreterContext context) {
        func.apply(oldObject, newObject, context);
      }
    };
    angularWatch(name, noteId, w);
  }

  /**
   * Remove watcher
   * @param name
   * @param watcher
   */
  private void angularUnwatch(String name, String noteId, AngularObjectWatcher watcher) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    if (registry.get(name, noteId) != null) {
      registry.get(name, noteId).removeWatcher(watcher);
    }
  }

  /**
   * Remove all watchers for the angular variable
   * @param name
   */
  private void angularUnwatch(String name, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    if (registry.get(name, noteId) != null) {
      registry.get(name, noteId).clearAllWatchers();
    }
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  private void angularUnbind(String name, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    registry.remove(name, noteId);
  }
}
