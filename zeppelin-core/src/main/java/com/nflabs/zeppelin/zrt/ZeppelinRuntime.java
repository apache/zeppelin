package com.nflabs.zeppelin.zrt;

import java.net.URI;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
	private static final String prefix = "zr";
	private ZeppelinConfiguration conf;
	private SharkContext sharkContext;
	private SparkContext sparkContext;
	private User user;
	private Date dateCreated;
	AtomicLong tableId = new AtomicLong();
	
	public ZeppelinRuntime(ZeppelinConfiguration conf, User user){
		this.conf = conf;
		this.user = user;
		this.dateCreated = new Date();
		String sparkMaster = getEnv("MASTER", "local");
		String sparkHome = getEnv("SPARK_HOME", "./");
		
		sparkContext = sharkContext = new SharkContext(sparkMaster, jobName(), sparkHome, null, scala.collection.JavaConversions.mapAsScalaMap(System.getenv()));
		SharkEnv.sc_$eq(sharkContext);

	}
	
	public String jobName(){
		return prefix+"_"+user.getName()+"_"+dateCreated;
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
	

	public String genTableName(){
		return prefix+"_"+user.getName()+"_"+tableId.getAndIncrement();
	}
	
	public ZDD fromText(Schema schema, URI location, char split) throws ZeppelinRuntimeException{
		String tableName = genTableName();
		String tc = 
				"CREATE EXTERNAL TABLE "+ tableName+
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
		
		return fromTable(tableName);
	}
	


	public ZDD fromTable(String tableName) throws ZeppelinRuntimeException{
		TableRDD rdd;
		try{
			rdd = sharkContext.sql2rdd("select * from "+tableName);
		} catch(Exception e){
			throw new ZeppelinRuntimeException(e);
		}
		rdd.setName(tableName);
		return new ZDD(rdd);
	}
	

	public ZDD fromRDD(RDD rdd, Schema schema){
		return fromRDD(rdd, schema, genTableName());
	}
	
	public ZDD fromRDD(RDD rdd, Schema schema, String tableName){
		// TODO
		return null;
	}
	
	public ZDD fromSql(String sql) throws ZeppelinRuntimeException{
		return fromSql(sql, genTableName());
	}
	
	public ZDD fromSql(String sql, String tableName) throws ZeppelinRuntimeException{
		try{
			sharkContext.sql("CREATE VIEW "+tableName+" as "+sql, 0);
			return fromTable(tableName);			
		} catch(Exception e){
			throw new ZeppelinRuntimeException(e);
		}
	}
	
	
	public void drop(ZDD zdd) throws ZeppelinRuntimeException{
		try{
			sharkContext.sql("drop table if exists "+ zdd.tableName(), 0);
			sharkContext.sql("drop view if exists "+ zdd.tableName(), 0);
		} catch(Exception e){
			throw new ZeppelinRuntimeException(e);
		}
	}
}
