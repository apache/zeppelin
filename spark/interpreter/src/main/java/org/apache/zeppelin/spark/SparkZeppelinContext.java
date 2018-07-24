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

import com.google.common.collect.Lists;
import org.apache.spark.SparkContext;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.display.AngularObjectWatcher;
import org.apache.zeppelin.display.ui.OptionInput;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry;
import scala.Tuple2;
import scala.Unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static scala.collection.JavaConversions.asJavaIterable;
import static scala.collection.JavaConversions.collectionAsScalaIterable;


/**
 * ZeppelinContext for Spark
 */
public class SparkZeppelinContext extends BaseZeppelinContext {

  private SparkContext sc;
  private List<Class> supportedClasses;
  private Map<String, String> interpreterClassMap;
  private SparkShims sparkShims;

  public SparkZeppelinContext(
      SparkContext sc,
      SparkShims sparkShims,
      InterpreterHookRegistry hooks,
      int maxResult) {
    super(hooks, maxResult);
    this.sc = sc;
    this.sparkShims = sparkShims;
    interpreterClassMap = new HashMap();
    interpreterClassMap.put("spark", "org.apache.zeppelin.spark.SparkInterpreter");
    interpreterClassMap.put("sql", "org.apache.zeppelin.spark.SparkSqlInterpreter");
    interpreterClassMap.put("dep", "org.apache.zeppelin.spark.DepInterpreter");
    interpreterClassMap.put("pyspark", "org.apache.zeppelin.spark.PySparkInterpreter");

    this.supportedClasses = new ArrayList<>();
    try {
      supportedClasses.add(this.getClass().forName("org.apache.spark.sql.Dataset"));
    } catch (ClassNotFoundException e) {
    }

    try {
      supportedClasses.add(this.getClass().forName("org.apache.spark.sql.DataFrame"));
    } catch (ClassNotFoundException e) {
    }

    if (supportedClasses.isEmpty()) {
      throw new RuntimeException("Can not load Dataset/DataFrame class");
    }
  }

  @Override
  public List<Class> getSupportedClasses() {
    return supportedClasses;
  }

  @Override
  public Map<String, String> getInterpreterClassMap() {
    return interpreterClassMap;
  }

  @Override
  public String showData(Object obj) {
    return sparkShims.showDataFrame(obj, maxResult);
  }

  @ZeppelinApi
  public Object select(String name, scala.collection.Iterable<Tuple2<Object, String>> options) {
    return select(name, null, options);
  }

  @ZeppelinApi
  public Object select(String name, Object defaultValue,
                       scala.collection.Iterable<Tuple2<Object, String>> options) {
    return select(name, defaultValue, tuplesToParamOptions(options));
  }

  @ZeppelinApi
  public scala.collection.Seq<Object> checkbox(
      String name,
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    List<Object> allChecked = new LinkedList<>();
    for (Tuple2<Object, String> option : asJavaIterable(options)) {
      allChecked.add(option._1());
    }
    return checkbox(name, collectionAsScalaIterable(allChecked), options);
  }

  @ZeppelinApi
  public scala.collection.Seq<Object> checkbox(
      String name,
      scala.collection.Iterable<Object> defaultChecked,
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    List<Object> defaultCheckedList = Lists.newArrayList(asJavaIterable(defaultChecked).iterator());
    Collection<Object> checkbox = checkbox(name, defaultCheckedList, tuplesToParamOptions(options));
    List<Object> checkboxList = Arrays.asList(checkbox.toArray());
    return scala.collection.JavaConversions.asScalaBuffer(checkboxList).toSeq();
  }

  @ZeppelinApi
  public Object noteSelect(String name, scala.collection.Iterable<Tuple2<Object, String>> options) {
    return noteSelect(name, "", options);
  }

  @ZeppelinApi
  public Object noteSelect(String name, Object defaultValue,
                           scala.collection.Iterable<Tuple2<Object, String>> options) {
    return noteSelect(name, defaultValue, tuplesToParamOptions(options));
  }

  @ZeppelinApi
  public scala.collection.Seq<Object> noteCheckbox(
      String name,
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    List<Object> allChecked = new LinkedList<>();
    for (Tuple2<Object, String> option : asJavaIterable(options)) {
      allChecked.add(option._1());
    }
    return noteCheckbox(name, collectionAsScalaIterable(allChecked), options);
  }

  @ZeppelinApi
  public scala.collection.Seq<Object> noteCheckbox(
      String name,
      scala.collection.Iterable<Object> defaultChecked,
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    List<Object> defaultCheckedList = Lists.newArrayList(asJavaIterable(defaultChecked).iterator());
    Collection<Object> checkbox = noteCheckbox(name, defaultCheckedList,
        tuplesToParamOptions(options));
    List<Object> checkboxList = Arrays.asList(checkbox.toArray());
    return scala.collection.JavaConversions.asScalaBuffer(checkboxList).toSeq();
  }

  private OptionInput.ParamOption[] tuplesToParamOptions(
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    int n = options.size();
    OptionInput.ParamOption[] paramOptions = new OptionInput.ParamOption[n];
    Iterator<Tuple2<Object, String>> it = asJavaIterable(options).iterator();

    int i = 0;
    while (it.hasNext()) {
      Tuple2<Object, String> valueAndDisplayValue = it.next();
      paramOptions[i++] = new OptionInput.ParamOption(valueAndDisplayValue._1(),
          valueAndDisplayValue._2());
    }

    return paramOptions;
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
}
