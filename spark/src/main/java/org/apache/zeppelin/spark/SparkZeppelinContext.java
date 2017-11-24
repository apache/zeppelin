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
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.display.AngularObjectWatcher;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.display.ui.OptionInput;
import org.apache.zeppelin.interpreter.*;
import scala.Tuple2;
import scala.Unit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static scala.collection.JavaConversions.asJavaIterable;
import static scala.collection.JavaConversions.collectionAsScalaIterable;

/**
 * ZeppelinContext for Spark
 */
public class SparkZeppelinContext extends BaseZeppelinContext {


  private SparkContext sc;
  public SQLContext sqlContext;
  private List<Class> supportedClasses;
  private Map<String, String> interpreterClassMap;

  public SparkZeppelinContext(
      SparkContext sc, SQLContext sql,
      InterpreterHookRegistry hooks,
      int maxResult) {
    super(hooks, maxResult);
    this.sc = sc;
    this.sqlContext = sql;

    interpreterClassMap = new HashMap<String, String>();
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

    try {
      supportedClasses.add(this.getClass().forName("org.apache.spark.sql.SchemaRDD"));
    } catch (ClassNotFoundException e) {
    }

    if (supportedClasses.isEmpty()) {
      throw new RuntimeException("Can not load Dataset/DataFrame/SchemaRDD class");
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
  public String showData(Object df) {
    Object[] rows = null;
    Method take;
    String jobGroup = Utils.buildJobGroupId(interpreterContext);
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
      throw new RuntimeException(e);
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
      throw new RuntimeException(e);
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
      throw new RuntimeException(e);
    }

    if (rows.length > maxResult) {
      msg.append("\n");
      msg.append(ResultMessages.getExceedsLimitRowsMessage(maxResult,
          SparkSqlInterpreter.MAX_RESULTS));
    }

    sc.clearJobGroup();
    return msg.toString();
  }

  @ZeppelinApi
  public Object select(String name, scala.collection.Iterable<Tuple2<Object, String>> options) {
    return select(name, "", options);
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
