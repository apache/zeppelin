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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.spark.SparkContext;
import org.apache.spark.scheduler.ActiveJob;
import org.apache.spark.scheduler.DAGScheduler;
import org.apache.spark.scheduler.Stage;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.ui.jobs.JobProgressListener;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.JavaConverters;
import scala.collection.mutable.HashMap;
import scala.collection.mutable.HashSet;

/**
 * Spark SQL interpreter for Zeppelin.
 *
 * @author Leemoonsoo
 *
 */
public class SparkSqlInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(SparkSqlInterpreter.class);
  AtomicInteger num = new AtomicInteger(0);

  static {
    Interpreter.register(
        "sql",
        "spark",
        SparkSqlInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add("zeppelin.spark.maxResult",
                SparkInterpreter.getSystemDefault("ZEPPELIN_SPARK_MAXRESULT",
                    "zeppelin.spark.maxResult", "1000"),
                "Max number of SparkSQL result to display.")
            .add("zeppelin.spark.concurrentSQL",
                SparkInterpreter.getSystemDefault("ZEPPELIN_SPARK_CONCURRENTSQL",
                    "zeppelin.spark.concurrentSQL", "false"),
                "Execute multiple SQL concurrently if set true.")
            .build());
  }

  private String getJobGroup(InterpreterContext context){
    return "zeppelin-" + context.getParagraphId();
  }

  private int maxResult;

  public SparkSqlInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    this.maxResult = Integer.parseInt(getProperty("zeppelin.spark.maxResult"));
  }

  private SparkInterpreter getSparkInterpreter() {
    for (Interpreter intp : getInterpreterGroup()) {
      if (intp.getClassName().equals(SparkInterpreter.class.getName())) {
        Interpreter p = intp;
        while (p instanceof WrappedInterpreter) {
          if (p instanceof LazyOpenInterpreter) {
            p.open();
          }
          p = ((WrappedInterpreter) p).getInnerInterpreter();
        }
        return (SparkInterpreter) p;
      }
    }
    return null;
  }

  public boolean concurrentSQL() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.concurrentSQL"));
  }

  @Override
  public void close() {}

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    SQLContext sqlc = null;

    sqlc = getSparkInterpreter().getSQLContext();

    SparkContext sc = sqlc.sparkContext();
    if (concurrentSQL()) {
      sc.setLocalProperty("spark.scheduler.pool", "fair");
    } else {
      sc.setLocalProperty("spark.scheduler.pool", null);
    }

    try {
      Object rdd = sqlc.sql(st);
      String msg = ZeppelinContext.showRDD(sc, context, rdd, maxResult);
      return new InterpreterResult(Code.SUCCESS, msg);
    } catch (Exception e) {
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    SQLContext sqlc = getSparkInterpreter().getSQLContext();
    SparkContext sc = sqlc.sparkContext();

    sc.cancelJobGroup(getJobGroup(context));
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }


  @Override
  public int getProgress(InterpreterContext context) {
    String jobGroup = getJobGroup(context);
    SQLContext sqlc = getSparkInterpreter().getSQLContext();
    SparkContext sc = sqlc.sparkContext();
    JobProgressListener sparkListener = getSparkInterpreter().getJobProgressListener();
    int completedTasks = 0;
    int totalTasks = 0;

    DAGScheduler scheduler = sc.dagScheduler();
    HashSet<ActiveJob> jobs = scheduler.activeJobs();
    Iterator<ActiveJob> it = jobs.iterator();
    while (it.hasNext()) {
      ActiveJob job = it.next();
      String g = (String) job.properties().get("spark.jobGroup.id");
      if (jobGroup.equals(g)) {
        int[] progressInfo = null;
        if (sc.version().startsWith("1.0")) {
          progressInfo = getProgressFromStage_1_0x(sparkListener, job.finalStage());
        } else if (sc.version().startsWith("1.1")) {
          progressInfo = getProgressFromStage_1_1x(sparkListener, job.finalStage());
        } else if (sc.version().startsWith("1.2")) {
          progressInfo = getProgressFromStage_1_1x(sparkListener, job.finalStage());
        } else if (sc.version().startsWith("1.3")) {
          progressInfo = getProgressFromStage_1_1x(sparkListener, job.finalStage());
        } else if (sc.version().startsWith("1.4")) {
          progressInfo = getProgressFromStage_1_1x(sparkListener, job.finalStage());
        } else {
          logger.warn("Spark {} getting progress information not supported" + sc.version());
          continue;
        }
        totalTasks += progressInfo[0];
        completedTasks += progressInfo[1];
      }
    }

    if (totalTasks == 0) {
      return 0;
    }
    return completedTasks * 100 / totalTasks;
  }

  private int[] getProgressFromStage_1_0x(JobProgressListener sparkListener, Stage stage) {
    int numTasks = stage.numTasks();
    int completedTasks = 0;

    Method method;
    Object completedTaskInfo = null;
    try {
      method = sparkListener.getClass().getMethod("stageIdToTasksComplete");
      completedTaskInfo =
          JavaConversions.asJavaMap((HashMap<Object, Object>) method.invoke(sparkListener)).get(
              stage.id());
    } catch (NoSuchMethodException | SecurityException e) {
      logger.error("Error while getting progress", e);
    } catch (IllegalAccessException e) {
      logger.error("Error while getting progress", e);
    } catch (IllegalArgumentException e) {
      logger.error("Error while getting progress", e);
    } catch (InvocationTargetException e) {
      logger.error("Error while getting progress", e);
    }

    if (completedTaskInfo != null) {
      completedTasks += (int) completedTaskInfo;
    }
    List<Stage> parents = JavaConversions.asJavaList(stage.parents());
    if (parents != null) {
      for (Stage s : parents) {
        int[] p = getProgressFromStage_1_0x(sparkListener, s);
        numTasks += p[0];
        completedTasks += p[1];
      }
    }

    return new int[] {numTasks, completedTasks};
  }

  private int[] getProgressFromStage_1_1x(JobProgressListener sparkListener, Stage stage) {
    int numTasks = stage.numTasks();
    int completedTasks = 0;

    try {
      Method stageIdToData = sparkListener.getClass().getMethod("stageIdToData");
      HashMap<Tuple2<Object, Object>, Object> stageIdData =
          (HashMap<Tuple2<Object, Object>, Object>) stageIdToData.invoke(sparkListener);
      Class<?> stageUIDataClass =
          this.getClass().forName("org.apache.spark.ui.jobs.UIData$StageUIData");

      Method numCompletedTasks = stageUIDataClass.getMethod("numCompleteTasks");

      Set<Tuple2<Object, Object>> keys =
          JavaConverters.asJavaSetConverter(stageIdData.keySet()).asJava();
      for (Tuple2<Object, Object> k : keys) {
        if (stage.id() == (int) k._1()) {
          Object uiData = stageIdData.get(k).get();
          completedTasks += (int) numCompletedTasks.invoke(uiData);
        }
      }
    } catch (Exception e) {
      logger.error("Error on getting progress information", e);
    }

    List<Stage> parents = JavaConversions.asJavaList(stage.parents());
    if (parents != null) {
      for (Stage s : parents) {
        int[] p = getProgressFromStage_1_1x(sparkListener, s);
        numTasks += p[0];
        completedTasks += p[1];
      }
    }
    return new int[] {numTasks, completedTasks};
  }

  @Override
  public Scheduler getScheduler() {
    if (concurrentSQL()) {
      int maxConcurrency = 10;
      return SchedulerFactory.singleton().createOrGetParallelScheduler(
          SparkSqlInterpreter.class.getName() + this.hashCode(), maxConcurrency);
    } else {
      // getSparkInterpreter() calls open() inside.
      // That means if SparkInterpreter is not opened, it'll wait until SparkInterpreter open.
      // In this moment UI displays 'READY' or 'FINISHED' instead of 'PENDING' or 'RUNNING'.
      // It's because of scheduler is not created yet, and scheduler is created by this function.
      // Therefore, we can still use getSparkInterpreter() here, but it's better and safe
      // to getSparkInterpreter without opening it.
      for (Interpreter intp : getInterpreterGroup()) {
        if (intp.getClassName().equals(SparkInterpreter.class.getName())) {
          Interpreter p = intp;
          return p.getScheduler();
        } else {
          continue;
        }
      }
      throw new InterpreterException("Can't find SparkInterpreter");
    }
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }
}
