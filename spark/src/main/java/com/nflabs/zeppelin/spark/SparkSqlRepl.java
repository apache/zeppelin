package com.nflabs.zeppelin.spark;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.spark.sql.catalyst.expressions.Row;

import com.nflabs.zeppelin.repl.Repl;
import com.nflabs.zeppelin.repl.ReplResult;

public class SparkSqlRepl extends Repl {
	private Repl sparkRepl;
	AtomicInteger num = new AtomicInteger(0);

	public SparkSqlRepl(Properties property) {
		super(property);
	}

	@Override
	public void initialize() {
		Map<String, Repl> repls = (Map<String, Repl>) this.getProperty().get("repls");
		if(repls!=null) {
			sparkRepl = repls.get("spark");
		}
	}
	
	public void setSparkRepl(Repl repl) {
		this.sparkRepl = repl;
	}
	

	@Override
	public void destroy() {
	}

	@Override
	public Object getValue(String name) {
		return null;
	}

	@Override
	public ReplResult interpret(String st) {
		String valName = "zpSqlReplResult"+this.hashCode()+"_"+num.getAndIncrement();
		String escaped = st.replaceAll("\"","\\\"");
		ReplResult ret = sparkRepl.interpret("val "+valName+" = collection.convert.wrapAll.asJavaCollection(sqlc.sql(\""+escaped+"\").take(10000))");
		if(ret.code()!=ReplResult.Code.SUCCESS) {
			return ret;
		}
		Collection<Row> values = (Collection<Row>) sparkRepl.getValue(valName);
		return new ReplResult(ret.code(), ret.message(), (Serializable) values);
	}

	@Override
	public void cancel() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void bindValue(String name, Object o) {
		// TODO Auto-generated method stub
		
	}

}
