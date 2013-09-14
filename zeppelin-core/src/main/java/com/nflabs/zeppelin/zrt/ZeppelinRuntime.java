package com.nflabs.zeppelin.zrt;

import java.net.URI;
import java.util.Date;
import java.util.List;

import org.apache.spark.SparkContext;
import org.apache.spark.rdd.RDD;

import scala.collection.immutable.Seq;
import shark.SharkContext;
import shark.SharkEnv;
import shark.api.QueryExecutionException;
import shark.api.TableRDD;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.zai.Param;
import com.nflabs.zeppelin.zai.ZeppelinApplication;
import com.nflabs.zeppelin.zdd.Schema;
import com.nflabs.zeppelin.zdd.ZDD;

public class ZeppelinRuntime {	
	private ZeppelinConfiguration conf;
	private SharkContext sharkContext;
	private SparkContext sparkContext;
	private String name;
	private User user;
	
	public ZeppelinRuntime(ZeppelinConfiguration conf, User user){
		this.conf = conf;
		this.user = user;
		
		String sparkMaster = getEnv("MASTER", "local");
		String sparkHome = getEnv("SPARK_HOME", "./");
		this.name = "ZeppelinRuntime-"+new Date();
		
		sparkContext = sharkContext = new SharkContext(sparkMaster, name, sparkHome, null, scala.collection.JavaConversions.mapAsScalaMap(System.getenv()));
		SharkEnv.sc_$eq(sharkContext);

	}
	
	public String getName(){
		return name;
	}
	
	private String getEnv(String key, String def){
		String val = System.getenv(key);
		if(val==null) return def;
		else return val;
	}

	public void destroy(){
		
	}

	public List<ZDD> run(ZeppelinApplication za, List<ZDD> inputs, List<Param> params){
		return za.execute(inputs, params);
	}
	
	public ZDD fromText(Schema schema, URI location, char split) throws ZeppelinRuntimeException{
		String tc = 
				"CREATE EXTERNAL TABLE "+ schema.getName()+
				"("+schema.toHiveTableCreationQueryColumnPart()+") "+
				"ROW FORMAT DELIMITED FIELDS TERMINATED BY '"+split+"' "+
				"STORED AS TEXTFILE " +
				"LOCATION '"+location.toString()+"'"				
				;
		
		try{
			sharkContext.sql(tc, 0);
		} catch(Exception e){
			throw new ZeppelinRuntimeException(e);
		}
		
		return fromSql("select * from "+schema.getName());
	}
	

	public ZDD fromSql(String sql) throws ZeppelinRuntimeException{
		TableRDD rdd;
		try{
			rdd = sharkContext.sql2rdd(sql);
		} catch(Exception e){
			throw new ZeppelinRuntimeException(e);
		}
		return new ZDD(rdd);
	}
	
	public void drop(ZDD zdd) throws ZeppelinRuntimeException{
		try{
			sharkContext.sql("drop table "+zdd.schema().getName(), 0);
		} catch(Exception e){
			throw new ZeppelinRuntimeException(e);
		}
	}
	
}
