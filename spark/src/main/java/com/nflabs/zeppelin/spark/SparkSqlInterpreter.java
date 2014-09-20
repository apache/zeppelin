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

import scala.Option;
import scala.Tuple2;
import scala.collection.Iterable;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.JavaConverters;
import scala.collection.mutable.HashMap;
import scala.collection.mutable.HashSet;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.Interpreter.SchedulingMode;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;

public class SparkSqlInterpreter extends Interpreter {
	Logger logger = LoggerFactory.getLogger(SparkSqlInterpreter.class);
	AtomicInteger num = new AtomicInteger(0);
	
	static {
		Interpreter.register("sql", SparkSqlInterpreter.class.getName());
	}
	
	private final String jobGroup = "zeppelin-"+this.hashCode();
	
	public SparkSqlInterpreter(Properties property) {
		super(property);
	}

	@Override
	public void open() {
		
	}
	
	
	private SparkInterpreter getSparkInterpreter(){
		return SparkInterpreter.singleton(getProperty());
	}

	@Override
	public void close() {
	}

	@Override
	public Object getValue(String name) {
		return null;
	}

	@Override
	public InterpreterResult interpret(String st) {
		SQLContext sqlc = getSparkInterpreter().getSQLContext();
		SparkContext sc = sqlc.sparkContext();
		sc.setJobGroup(jobGroup, "Zeppelin", false);
		SchemaRDD rdd;
		Row[] rows = null;
		try {
			rdd = sqlc.sql(st);
			rows = rdd.take(10000);
		} catch(Exception e){
			logger.error("Error", e);
			sc.clearJobGroup();
			return new InterpreterResult(Code.ERROR, e.getMessage());
		}
		
		String msg = null;
		// get field names
		List<Attribute> columns = scala.collection.JavaConverters.asJavaListConverter(rdd.queryExecution().analyzed().output()).asJava();
		for(Attribute col : columns) {
			if(msg==null) {
				msg = col.name();
			} else {
				msg += "\t"+col.name();
			}
		}
		msg += "\n";
			
		// ArrayType, BinaryType, BooleanType, ByteType, DecimalType, DoubleType, DynamicType, FloatType, FractionalType, IntegerType, IntegralType, LongType, MapType, NativeType, NullType, NumericType, ShortType, StringType, StructType
		for(Row row : rows) {
			for(int i=0; i<columns.size(); i++){
				String type = columns.get(i).dataType().toString();
				if ("BooleanType".equals(type)) {
					msg += row.getBoolean(i);
				} else if("DecimalType".equals(type)) {
					msg += row.getInt(i);
				} else if("DoubleType".equals(type)) {
					msg += row.getDouble(i);
				} else if("FloatType".equals(type)) {
					msg += row.getFloat(i);
				} else if("LongType".equals(type)) {
					msg += row.getLong(i);
				} else if("IntegerType".equals(type)) {
					msg += row.getInt(i);
				} else if("ShortType".equals(type)) {
					msg += row.getShort(i);
				} else if("StringType".equals(type)) {
					msg += row.getString(i);
				} else {
					msg += row.getString(i);
				}
				if(i!=columns.size()-1){
					msg += "\t";
				}
			}
			msg += "\n";
		}
		InterpreterResult rett = new InterpreterResult(Code.SUCCESS, "%table "+msg);
		sc.clearJobGroup();
		return rett;
	}
	

	@Override
	public void cancel() {
		SQLContext sqlc = getSparkInterpreter().getSQLContext();
		SparkContext sc = sqlc.sparkContext();

		sc.cancelJobGroup(jobGroup);
	}

	@Override
	public void bindValue(String name, Object o) {
		
	}

	@Override
	public FormType getFormType() {
		return FormType.SIMPLE;
	}


	public int getProgress(){
		SQLContext sqlc = getSparkInterpreter().getSQLContext();
		SparkContext sc = sqlc.sparkContext();
		JobProgressListener sparkListener = getSparkInterpreter().getJobProgressListener();
		int completedTasks = 0;
		int totalTasks = 0;

		DAGScheduler scheduler = sc.dagScheduler();
		HashSet<ActiveJob> jobs = scheduler.activeJobs();
		Iterator<ActiveJob> it = jobs.iterator();
		while(it.hasNext()) {
			ActiveJob job = it.next();
			String g = (String) job.properties().get("spark.jobGroup.id");
			if (jobGroup.equals(g)) {
				int[] progressInfo = null; 
				if (sc.version().startsWith("1.0")) {
					progressInfo = getProgressFromStage_1_0x(sparkListener, job.finalStage());
				} else if (sc.version().startsWith("1.1")){
					progressInfo = getProgressFromStage_1_1x(sparkListener, job.finalStage());
				} else {
					logger.warn("Spark {} getting progress information not supported"+sc.version());
					continue;
				}
				totalTasks+=progressInfo[0];
				completedTasks+=progressInfo[1];
			}
		}

		if(totalTasks==0) return 0;
		return completedTasks*100/totalTasks;
	}

	private int [] getProgressFromStage_1_0x(JobProgressListener sparkListener, Stage stage){
		int numTasks = stage.numTasks();
		int completedTasks = 0;
		
		Method method;
		Object completedTaskInfo = null;
		try {
			method = sparkListener.getClass().getMethod("stageIdToTasksComplete");
			completedTaskInfo = JavaConversions.asJavaMap((HashMap<Object, Object>)method.invoke(sparkListener)).get(stage.id());
		} catch (NoSuchMethodException | SecurityException e) {
			logger.error("Error while getting progress", e);			
		} catch (IllegalAccessException e) {
			logger.error("Error while getting progress", e);
		} catch (IllegalArgumentException e) {
			logger.error("Error while getting progress", e);
		} catch (InvocationTargetException e) {
			logger.error("Error while getting progress", e);
		}
		
		if(completedTaskInfo!=null) {
			completedTasks += (int) completedTaskInfo;
		}
		List<Stage> parents = JavaConversions.asJavaList(stage.parents());
		if(parents!=null) {
			for(Stage s : parents) {
				int[] p = getProgressFromStage_1_0x(sparkListener, s);
				numTasks+= p[0];
				completedTasks+= p[1];
			}
		}
		
		return new int[]{numTasks, completedTasks};		
	}

	private int [] getProgressFromStage_1_1x(JobProgressListener sparkListener, Stage stage){
		int numTasks = stage.numTasks();
		int completedTasks = 0;
		
		try {
			Method stageIdToData = sparkListener.getClass().getMethod("stageIdToData");
			HashMap<Tuple2<Object, Object>, Object> stageIdData = (HashMap<Tuple2<Object, Object>, Object>)stageIdToData.invoke(sparkListener);
			Class<?> stageUIDataClass = this.getClass().forName("org.apache.spark.ui.jobs.UIData$StageUIData");

			Method numCompletedTasks = stageUIDataClass.getMethod("numCompleteTasks");
			
			Set<Tuple2<Object, Object>> keys = JavaConverters.asJavaSetConverter(stageIdData.keySet()).asJava();
			for(Tuple2<Object, Object> k : keys) {
				if(stage.id() == (int)k._1()) {
					Object uiData = stageIdData.get(k).get();
					completedTasks += (int)numCompletedTasks.invoke(uiData);
				}
			}
		} catch(Exception e) {
			logger.error("Error on getting progress information", e);
		}
		
		List<Stage> parents = JavaConversions.asJavaList(stage.parents());
		if (parents!=null) {
			for(Stage s : parents) {
				int[] p = getProgressFromStage_1_1x(sparkListener, s);
				numTasks+= p[0];
				completedTasks+= p[1];
			}
		}
		return new int[]{numTasks, completedTasks};		
	}

	@Override
	public SchedulingMode getSchedulingMode() {
		return SchedulingMode.FIFO;
	}

	@Override
	public List<String> completion(String buf, int cursor) {
		return null;
	}
}
