package com.nflabs.zeppelin.spark;

import java.util.List;
import java.util.Properties;
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

import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.mutable.HashSet;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;

public class SparkSqlInterpreter extends Interpreter {
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
		SchemaRDD rdd = sqlc.sql(st);
		Row[] rows = null;
		try {
			rows = rdd.take(10000);
		} catch(Exception e){
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

	@Override
	public int getProgress() {
		// howto get progress from sparkListener? check this out
		// https://github.com/apache/spark/blob/v1.0.1/core/src/main/scala/org/apache/spark/ui/jobs/StageTable.scala
		
		JobProgressListener sparkListener = getSparkInterpreter().getJobProgressListener();
		if(sparkListener==null) return -1;
		
		int completedTasks = 0;
		int totalTasks = 0;

		SQLContext sqlc = getSparkInterpreter().getSQLContext();
		SparkContext sc = sqlc.sparkContext();

		DAGScheduler scheduler = sc.dagScheduler();
		HashSet<ActiveJob> jobs = scheduler.activeJobs();
		Iterator<ActiveJob> it = jobs.iterator();
		while(it.hasNext()) {
			ActiveJob job = it.next();
			if(job==null || job.properties()==null) continue;
			
			String g = (String) job.properties().get("spark.jobGroup.id");
			if (jobGroup.equals(g)) {
				int[] progressInfo = getProgressFromStage(sparkListener, job.finalStage());
				totalTasks+=progressInfo[0];
				completedTasks+=progressInfo[1];
			}
		}

		if(totalTasks==0) return 0;
		return completedTasks*100/totalTasks;
	}
	
	private int [] getProgressFromStage(JobProgressListener sparkListener, Stage stage){
		int numTasks = stage.numTasks();
		int completedTasks = 0;
		Object completedTaskInfo = JavaConversions.asJavaMap(sparkListener.stageIdToTasksComplete()).get(stage.id());
		if(completedTaskInfo!=null) {
			completedTasks += (int) completedTaskInfo;
		}
		List<Stage> parents = JavaConversions.asJavaList(stage.parents());
		if(parents!=null) {
			for(Stage s : parents) {
				int[] p = getProgressFromStage(sparkListener, s);
				numTasks+= p[0];
				completedTasks+= p[1];
			}
		}
		
		return new int[]{numTasks, completedTasks};		
	}

}
