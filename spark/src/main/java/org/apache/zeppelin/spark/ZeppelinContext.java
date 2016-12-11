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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.annotation.Experimental;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectWatcher;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input.ParamOption;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry;
import org.apache.zeppelin.interpreter.RemoteWorksController;
import org.apache.zeppelin.spark.dep.SparkDependencyResolver;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.ResourceSet;

import scala.Tuple2;
import scala.Unit;

/**
 * Spark context for zeppelin.
 */
public class ZeppelinContext {
  // Map interpreter class name (to be used by hook registry) from
  // given replName in parapgraph
  private static final Map<String, String> interpreterClassMap;
  static {
    interpreterClassMap = new HashMap<>();
    interpreterClassMap.put("spark", "org.apache.zeppelin.spark.SparkInterpreter");
    interpreterClassMap.put("sql", "org.apache.zeppelin.spark.SparkSqlInterpreter");
    interpreterClassMap.put("dep", "org.apache.zeppelin.spark.DepInterpreter");
    interpreterClassMap.put("pyspark", "org.apache.zeppelin.spark.PySparkInterpreter");
  }
  
  private SparkDependencyResolver dep;
  private InterpreterContext interpreterContext;
  private int maxResult;
  private List<Class> supportedClasses;
  private InterpreterHookRegistry hooks;
  
  public ZeppelinContext(SparkContext sc, SQLContext sql,
      InterpreterContext interpreterContext,
      SparkDependencyResolver dep,
      InterpreterHookRegistry hooks,
      int maxResult) {
    this.sc = sc;
    this.sqlContext = sql;
    this.interpreterContext = interpreterContext;
    this.dep = dep;
    this.hooks = hooks;
    this.maxResult = maxResult;
    this.supportedClasses = new ArrayList<>();
    try {
      supportedClasses.add(this.getClass().forName("org.apache.spark.sql.Dataset"));
    } catch (ClassNotFoundException e) {
    }

    try {
      supportedClasses.add(this.getClass().forName("org.apache.spark.sql.DataFrame"));
    } catch (ClassNotFoundException e) {
    }

    try {
      supportedClasses.add(this.getClass().forName("org.apache.spark.sql.SchemaRDD"));
    } catch (ClassNotFoundException e) {
    }

    if (supportedClasses.isEmpty()) {
      throw new InterpreterException("Can not road Dataset/DataFrame/SchemaRDD class");
    }
  }

  public SparkContext sc;
  public SQLContext sqlContext;
  private GUI gui;

  @ZeppelinApi
  public Object input(String name) {
    return input(name, "");
  }

  @ZeppelinApi
  public Object input(String name, Object defaultValue) {
    return gui.input(name, defaultValue);
  }

  @ZeppelinApi
  public Object select(String name, scala.collection.Iterable<Tuple2<Object, String>> options) {
    return select(name, "", options);
  }

  @ZeppelinApi
  public Object select(String name, Object defaultValue,
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    return gui.select(name, defaultValue, tuplesToParamOptions(options));
  }

  @ZeppelinApi
  public scala.collection.Iterable<Object> checkbox(String name,
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    List<Object> allChecked = new LinkedList<>();
    for (Tuple2<Object, String> option : asJavaIterable(options)) {
      allChecked.add(option._1());
    }
    return checkbox(name, collectionAsScalaIterable(allChecked), options);
  }

  @ZeppelinApi
  public scala.collection.Iterable<Object> checkbox(String name,
      scala.collection.Iterable<Object> defaultChecked,
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    return collectionAsScalaIterable(gui.checkbox(name, asJavaCollection(defaultChecked),
      tuplesToParamOptions(options)));
  }

  private ParamOption[] tuplesToParamOptions(
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    int n = options.size();
    ParamOption[] paramOptions = new ParamOption[n];
    Iterator<Tuple2<Object, String>> it = asJavaIterable(options).iterator();

    int i = 0;
    while (it.hasNext()) {
      Tuple2<Object, String> valueAndDisplayValue = it.next();
      paramOptions[i++] = new ParamOption(valueAndDisplayValue._1(), valueAndDisplayValue._2());
    }

    return paramOptions;
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
  @ZeppelinApi
  public void show(Object o) {
    show(o, maxResult);
  }

  /**
   * show DataFrame or SchemaRDD
   * @param o DataFrame or SchemaRDD object
   * @param maxResult maximum number of rows to display
   */

  @ZeppelinApi
  public void show(Object o, int maxResult) {
    try {
      if (supportedClasses.contains(o.getClass())) {
        interpreterContext.out.write(showDF(sc, interpreterContext, o, maxResult));
      } else {
        interpreterContext.out.write(o.toString());
      }
    } catch (IOException e) {
      throw new InterpreterException(e);
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
      // convert it to DataFrame if it is Dataset, as we will iterate all the records
      // and assume it is type Row.
      if (df.getClass().getCanonicalName().equals("org.apache.spark.sql.Dataset")) {
        Method convertToDFMethod = df.getClass().getMethod("toDF");
        df = convertToDFMethod.invoke(df);
      }
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

    StringBuilder msg = new StringBuilder();
    msg.append("%table ");
    for (Attribute col : columns) {
      msg.append(col.name() + "\t");
    }
    String trim = msg.toString().trim();
    msg = new StringBuilder(trim);
    msg.append("\n");

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
            msg.append(apply.invoke(row, i).toString());
          } else {
            msg.append("null");
          }
          if (i != columns.size() - 1) {
            msg.append("\t");
          }
        }
        msg.append("\n");
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      throw new InterpreterException(e);
    }

    if (rows.length > maxResult) {
      msg.append("\n<font color=red>Results are limited by " + maxResult + ".</font>");
    }
    sc.clearJobGroup();
    return msg.toString();
  }

  /**
   * Run paragraph by id
   * @param noteId
   * @param paragraphId
   */
  @ZeppelinApi
  public void run(String noteId, String paragraphId) {
    run(noteId, paragraphId, interpreterContext);
  }

  /**
   * Run paragraph by id
   * @param paragraphId
   */
  @ZeppelinApi
  public void run(String paragraphId) {
    String noteId = interpreterContext.getNoteId();
    run(noteId, paragraphId, interpreterContext);
  }

  /**
   * Run paragraph by id
   * @param noteId
   * @param context
   */
  @ZeppelinApi
  public void run(String noteId, String paragraphId, InterpreterContext context) {
    if (paragraphId.equals(context.getParagraphId())) {
      throw new InterpreterException("Can not run current Paragraph");
    }

    List<InterpreterContextRunner> runners =
        getInterpreterContextRunner(noteId, paragraphId, context);

    if (runners.size() <= 0) {
      throw new InterpreterException("Paragraph " + paragraphId + " not found " + runners.size());
    }

    for (InterpreterContextRunner r : runners) {
      r.run();
    }

  }

  public void runNote(String noteId) {
    runNote(noteId, interpreterContext);
  }

  public void runNote(String noteId, InterpreterContext context) {
    String runningNoteId = context.getNoteId();
    String runningParagraphId = context.getParagraphId();
    List<InterpreterContextRunner> runners = getInterpreterContextRunner(noteId, context);

    if (runners.size() <= 0) {
      throw new InterpreterException("Note " + noteId + " not found " + runners.size());
    }

    for (InterpreterContextRunner r : runners) {
      if (r.getNoteId().equals(runningNoteId) && r.getParagraphId().equals(runningParagraphId)) {
        continue;
      }
      r.run();
    }
  }


  /**
   * get Zeppelin Paragraph Runner from zeppelin server
   * @param noteId
   */
  @ZeppelinApi
  public List<InterpreterContextRunner> getInterpreterContextRunner(
      String noteId, InterpreterContext interpreterContext) {
    List<InterpreterContextRunner> runners = new LinkedList<>();
    RemoteWorksController remoteWorksController = interpreterContext.getRemoteWorksController();

    if (remoteWorksController != null) {
      runners = remoteWorksController.getRemoteContextRunner(noteId);
    }

    return runners;
  }

  /**
   * get Zeppelin Paragraph Runner from zeppelin server
   * @param noteId
   * @param paragraphId
   */
  @ZeppelinApi
  public List<InterpreterContextRunner> getInterpreterContextRunner(
      String noteId, String paragraphId, InterpreterContext interpreterContext) {
    List<InterpreterContextRunner> runners = new LinkedList<>();
    RemoteWorksController remoteWorksController = interpreterContext.getRemoteWorksController();

    if (remoteWorksController != null) {
      runners = remoteWorksController.getRemoteContextRunner(noteId, paragraphId);
    }

    return runners;
  }

  /**
   * Run paragraph at idx
   * @param idx
   */
  @ZeppelinApi
  public void run(int idx) {
    String noteId = interpreterContext.getNoteId();
    run(noteId, idx, interpreterContext);
  }

  /**
   * Run paragraph at index
   * @param idx index starting from 0
   * @param context interpreter context
   */
  public void run(String noteId, int idx, InterpreterContext context) {
    List<InterpreterContextRunner> runners = getInterpreterContextRunner(noteId, context);
    if (idx >= runners.size()) {
      throw new InterpreterException("Index out of bound");
    }

    InterpreterContextRunner runner = runners.get(idx);
    if (runner.getParagraphId().equals(context.getParagraphId())) {
      throw new InterpreterException("Can not run current Paragraph");
    }

    runner.run();
  }

  @ZeppelinApi
  public void run(List<Object> paragraphIdOrIdx) {
    run(paragraphIdOrIdx, interpreterContext);
  }

  /**
   * Run paragraphs
   * @param paragraphIdOrIdx list of paragraph id or idx
   */
  @ZeppelinApi
  public void run(List<Object> paragraphIdOrIdx, InterpreterContext context) {
    String noteId = context.getNoteId();
    for (Object idOrIdx : paragraphIdOrIdx) {
      if (idOrIdx instanceof String) {
        String paragraphId = (String) idOrIdx;
        run(noteId, paragraphId, context);
      } else if (idOrIdx instanceof Integer) {
        Integer idx = (Integer) idOrIdx;
        run(noteId, idx, context);
      } else {
        throw new InterpreterException("Paragraph " + idOrIdx + " not found");
      }
    }
  }

  @ZeppelinApi
  public void runAll() {
    runAll(interpreterContext);
  }

  /**
   * Run all paragraphs. except this.
   */
  @ZeppelinApi
  public void runAll(InterpreterContext context) {
    runNote(context.getNoteId());
  }

  @ZeppelinApi
  public List<String> listParagraphs() {
    List<String> paragraphs = new LinkedList<>();

    for (InterpreterContextRunner r : interpreterContext.getRunners()) {
      paragraphs.add(r.getParagraphId());
    }

    return paragraphs;
  }


  private AngularObject getAngularObject(String name, InterpreterContext interpreterContext) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    String noteId = interpreterContext.getNoteId();
    // try get local object
    AngularObject paragraphAo = registry.get(name, noteId, interpreterContext.getParagraphId());
    AngularObject noteAo = registry.get(name, noteId, null);

    AngularObject ao = paragraphAo != null ? paragraphAo : noteAo;

    if (ao == null) {
      // then global object
      ao = registry.get(name, null, null);
    }
    return ao;
  }


  /**
   * Get angular object. Look up notebook scope first and then global scope
   * @param name variable name
   * @return value
   */
  @ZeppelinApi
  public Object angular(String name) {
    AngularObject ao = getAngularObject(name, interpreterContext);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  /**
   * Get angular object. Look up global scope
   * @param name variable name
   * @return value
   */
  @Deprecated
  public Object angularGlobal(String name) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    AngularObject ao = registry.get(name, null, null);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  /**
   * Create angular variable in notebook scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  @ZeppelinApi
  public void angularBind(String name, Object o) {
    angularBind(name, o, interpreterContext.getNoteId());
  }

  /**
   * Create angular variable in global scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  @Deprecated
  public void angularBindGlobal(String name, Object o) {
    angularBind(name, o, (String) null);
  }

  /**
   * Create angular variable in local scope and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  @ZeppelinApi
  public void angularBind(String name, Object o, AngularObjectWatcher watcher) {
    angularBind(name, o, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Create angular variable in global scope and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  @Deprecated
  public void angularBindGlobal(String name, Object o, AngularObjectWatcher watcher) {
    angularBind(name, o, null, watcher);
  }

  /**
   * Add watcher into angular variable (local scope)
   * @param name name of the variable
   * @param watcher watcher
   */
  @ZeppelinApi
  public void angularWatch(String name, AngularObjectWatcher watcher) {
    angularWatch(name, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Add watcher into angular variable (global scope)
   * @param name name of the variable
   * @param watcher watcher
   */
  @Deprecated
  public void angularWatchGlobal(String name, AngularObjectWatcher watcher) {
    angularWatch(name, null, watcher);
  }

  @ZeppelinApi
  public void angularWatch(String name,
      final scala.Function2<Object, Object, Unit> func) {
    angularWatch(name, interpreterContext.getNoteId(), func);
  }

  @Deprecated
  public void angularWatchGlobal(String name,
      final scala.Function2<Object, Object, Unit> func) {
    angularWatch(name, null, func);
  }

  @ZeppelinApi
  public void angularWatch(
      String name,
      final scala.Function3<Object, Object, InterpreterContext, Unit> func) {
    angularWatch(name, interpreterContext.getNoteId(), func);
  }

  @Deprecated
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
  @ZeppelinApi
  public void angularUnwatch(String name, AngularObjectWatcher watcher) {
    angularUnwatch(name, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Remove watcher from angular variable (global)
   * @param name
   * @param watcher
   */
  @Deprecated
  public void angularUnwatchGlobal(String name, AngularObjectWatcher watcher) {
    angularUnwatch(name, null, watcher);
  }


  /**
   * Remove all watchers for the angular variable (local)
   * @param name
   */
  @ZeppelinApi
  public void angularUnwatch(String name) {
    angularUnwatch(name, interpreterContext.getNoteId());
  }

  /**
   * Remove all watchers for the angular variable (global)
   * @param name
   */
  @Deprecated
  public void angularUnwatchGlobal(String name) {
    angularUnwatch(name, (String) null);
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  @ZeppelinApi
  public void angularUnbind(String name) {
    String noteId = interpreterContext.getNoteId();
    angularUnbind(name, noteId);
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  @Deprecated
  public void angularUnbindGlobal(String name) {
    angularUnbind(name, null);
  }

  /**
   * Create angular variable in notebook scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  private void angularBind(String name, Object o, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId, null) == null) {
      registry.add(name, o, noteId, null);
    } else {
      registry.get(name, noteId, null).set(o);
    }
  }

  /**
   * Create angular variable in notebook scope and bind with front end Angular display
   * system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  private void angularBind(String name, Object o, String noteId, AngularObjectWatcher watcher) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId, null) == null) {
      registry.add(name, o, noteId, null);
    } else {
      registry.get(name, noteId, null).set(o);
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

    if (registry.get(name, noteId, null) != null) {
      registry.get(name, noteId, null).addWatcher(watcher);
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
    if (registry.get(name, noteId, null) != null) {
      registry.get(name, noteId, null).removeWatcher(watcher);
    }
  }

  /**
   * Remove all watchers for the angular variable
   * @param name
   */
  private void angularUnwatch(String name, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    if (registry.get(name, noteId, null) != null) {
      registry.get(name, noteId, null).clearAllWatchers();
    }
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  private void angularUnbind(String name, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    registry.remove(name, noteId, null);
  }

  /**
   * Get the interpreter class name from name entered in paragraph
   * @param replName if replName is a valid className, return that instead.
   */
  public String getClassNameFromReplName(String replName) {
    for (String name : interpreterClassMap.values()) {
      if (replName.equals(name)) {
        return replName;
      }
    }
    
    if (replName.contains("spark.")) {
      replName = replName.replace("spark.", "");
    }
    return interpreterClassMap.get(replName);
  }

  /**
   * General function to register hook event
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param cmd The code to be executed by the interpreter on given event
   * @param replName Name of the interpreter
   */
  @Experimental
  public void registerHook(String event, String cmd, String replName) {
    String noteId = interpreterContext.getNoteId();
    String className = getClassNameFromReplName(replName);
    hooks.register(noteId, className, event, cmd);
  }

  /**
   * registerHook() wrapper for current repl
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param cmd The code to be executed by the interpreter on given event
   */
  @Experimental
  public void registerHook(String event, String cmd) {
    String className = interpreterContext.getClassName();
    registerHook(event, cmd, className);
  }

  /**
   * Get the hook code
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param replName Name of the interpreter
   */
  @Experimental
  public String getHook(String event, String replName) {
    String noteId = interpreterContext.getNoteId();
    String className = getClassNameFromReplName(replName);
    return hooks.get(noteId, className, event);
  }

  /**
   * getHook() wrapper for current repl
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public String getHook(String event) {
    String className = interpreterContext.getClassName();
    return getHook(event, className);
  }

  /**
   * Unbind code from given hook event
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param replName Name of the interpreter
   */
  @Experimental
  public void unregisterHook(String event, String replName) {
    String noteId = interpreterContext.getNoteId();
    String className = getClassNameFromReplName(replName);
    hooks.unregister(noteId, className, event);
  }

  /**
   * unregisterHook() wrapper for current repl
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public void unregisterHook(String event) {
    String className = interpreterContext.getClassName();
    unregisterHook(event, className);
  }

  /**
   * Add object into resource pool
   * @param name
   * @param value
   */
  @ZeppelinApi
  public void put(String name, Object value) {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    resourcePool.put(name, value);
  }

  /**
   * Get object from resource pool
   * Search local process first and then the other processes
   * @param name
   * @return null if resource not found
   */
  @ZeppelinApi
  public Object get(String name) {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    Resource resource = resourcePool.get(name);
    if (resource != null) {
      return resource.get();
    } else {
      return null;
    }
  }

  /**
   * Remove object from resourcePool
   * @param name
   */
  @ZeppelinApi
  public void remove(String name) {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    resourcePool.remove(name);
  }

  /**
   * Check if resource pool has the object
   * @param name
   * @return
   */
  @ZeppelinApi
  public boolean containsKey(String name) {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    Resource resource = resourcePool.get(name);
    return resource != null;
  }

  /**
   * Get all resources
   */
  @ZeppelinApi
  public ResourceSet getAll() {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    return resourcePool.getAll();
  }

}
