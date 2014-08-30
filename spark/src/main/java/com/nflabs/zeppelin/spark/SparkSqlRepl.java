package com.nflabs.zeppelin.spark;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SchemaRDD;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Row;

import com.nflabs.zeppelin.interpreter.ClassloaderInterpreter;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;

public class SparkSqlRepl extends Interpreter {
	private ClassloaderInterpreter sparkClassloaderRepl;
	AtomicInteger num = new AtomicInteger(0);

	public SparkSqlRepl(Properties property) {
		super(property);
	}

	@Override
	public void initialize() {
		Map<String, Interpreter> repls = (Map<String, Interpreter>) this.getProperty().get("repls");
		if(repls!=null) {
			sparkClassloaderRepl = (ClassloaderInterpreter) repls.get("spark");
		}
	}
	
	public void setSparkClassloaderRepl(ClassloaderInterpreter repl) {
		this.sparkClassloaderRepl = (ClassloaderInterpreter) repl;
	}
	
	
	private void findSpark(){
		if(sparkClassloaderRepl!=null) return;
		Map<String, Interpreter> repls = (Map<String, Interpreter>) this.getProperty().get("repls");
		if(repls!=null) {			
			sparkClassloaderRepl = (ClassloaderInterpreter) repls.get("spark");
		}
	}
	

	@Override
	public void destroy() {
	}

	@Override
	public Object getValue(String name) {
		return null;
	}
	

	@Override
	public InterpreterResult interpret(String st) {
		findSpark();
		SQLContext sqlc = ((SparkRepl)sparkClassloaderRepl.getInnerRepl()).getSQLContext();
		SchemaRDD rdd = sqlc.sql(st);
		Row[] rows = null;
		try {
			rows = rdd.take(10000);
		} catch(Exception e){
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

		return new InterpreterResult(Code.SUCCESS, "%table "+msg);
	}

	@Override
	public void cancel() {

	}

	@Override
	public void bindValue(String name, Object o) {
		
	}

	@Override
	public FormType getFormType() {
		return FormType.SIMPLE;
	}

}
