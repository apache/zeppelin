package com.nflabs.zeppelin.spark;

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
import org.apache.spark.sql.SchemaRDD;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Row;
import org.apache.spark.ui.jobs.JobProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.JavaConverters;
import scala.collection.mutable.HashMap;
import scala.collection.mutable.HashSet;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterPropertyBuilder;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;
import com.nflabs.zeppelin.interpreter.WrappedInterpreter;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

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
            .add("zeppelin.spark.maxResult", "10000", "Max number of SparkSQL result to display.")
            .add("zeppelin.spark.useHiveContext", "false",
                "Use HiveContext instead of SQLContext if it is true.")
            .add("zeppelin.spark.concurrentSQL", "false",
                "Execute multiple SQL concurrently if set true.")
            .build());
  }

  private String getJobGroup(InterpreterContext context){
    return "zeppelin-" + this.hashCode() + "-" + context.getParagraph().getId();
  }

  private int maxResult;

  public SparkSqlInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    this.maxResult = conf.getInt("ZEPPELIN_SPARK_MAX_RESULT",
        "zeppelin.spark.maxResult",
        Integer.parseInt(getProperty("zeppelin.spark.maxResult")));
  }


  private SparkInterpreter getSparkInterpreter() {
    for (Interpreter intp : getInterpreterGroup()){
      if (intp.getClassName().equals(SparkInterpreter.class.getName())) {
        Interpreter p = intp;
        while (p instanceof WrappedInterpreter) {
          p = ((WrappedInterpreter) p).getInnerInterpreter();
        }
        return (SparkInterpreter) p;
      }
    }
    return null;
  }

  private boolean useHiveContext() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.useHiveContext"));
  }
  
  public boolean concurrentSQL() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.concurrentSQL"));
  }

  @Override
  public void close() {}

  @Override
  public Object getValue(String name) {
    return null;
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    SQLContext sqlc = null;

    if (useHiveContext()) {
      sqlc = getSparkInterpreter().getHiveContext();
    } else {
      sqlc = getSparkInterpreter().getSQLContext();
    }

    SparkContext sc = sqlc.sparkContext();
    if (concurrentSQL()) {
      sc.setLocalProperty("spark.scheduler.pool", "fair");
    } else {
      sc.setLocalProperty("spark.scheduler.pool", null);
    }

    sc.setJobGroup(getJobGroup(context), "Zeppelin", false);
    SchemaRDD rdd;
    Row[] rows = null;
    try {
      rdd = sqlc.sql(st);
      rows = rdd.take(maxResult + 1);
    } catch (Exception e) {
      logger.error("Error", e);
      sc.clearJobGroup();
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }

    String msg = null;
    // get field names
    List<Attribute> columns =
        scala.collection.JavaConverters.asJavaListConverter(
            rdd.queryExecution().analyzed().output()).asJava();
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

    for (int r = 0; r < maxResult && r < rows.length; r++) {
      Row row = rows[r];

      for (int i = 0; i < columns.size(); i++) {
        if (!row.isNullAt(i)) {
          msg += row.apply(i).toString();
        } else {
          msg += "null";
        }
        if (i != columns.size() - 1) {
          msg += "\t";
        }
      }
      msg += "\n";
    }

    if (rows.length > maxResult) {
      msg += "\n<font color=red>Results are limited by " + maxResult + ".</font>";
    }
    InterpreterResult rett = new InterpreterResult(Code.SUCCESS, "%table " + msg);
    sc.clearJobGroup();
    return rett;
  }

  @Override
  public void cancel(InterpreterContext context) {
    SQLContext sqlc = getSparkInterpreter().getSQLContext();
    SparkContext sc = sqlc.sparkContext();

    sc.cancelJobGroup(getJobGroup(context));
  }

  @Override
  public void bindValue(String name, Object o) {

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
    }
    return getSparkInterpreter().getScheduler();
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }
}
