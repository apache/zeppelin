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

	public ZDD [] run(ZeppelinApplication za, ZDD [] inputs, Param [] params){
		return za.run(inputs, params);
	}
	

	public String genTableName(){
		return prefix+"_"+user.getName()+"_"+tableId.getAndIncrement();
	}
	

	
	
	public List<String> sql(String query) throws ZeppelinRuntimeException{
		try{
			scala.collection.Seq<String> ret = sharkContext.sql(query, 1000);
			return scala.collection.JavaConversions.seqAsJavaList(ret);			
		} catch(Exception e){
			throw new ZeppelinRuntimeException(e);
		}		
	}
	
	public TableRDD sql2rdd(String sql) throws ZeppelinRuntimeException{
		try{
			return sharkContext.sql2rdd(sql);
		} catch(Exception e){
			throw new ZeppelinRuntimeException(e);
		}
	}
	
	public ZDD fromText(String name, Schema schema, String location, char split){
		return ZDD.createFromText(this, name, schema, location, split);
	}
	
	public ZDD fromText(Schema schema, String location, char split){
		return ZDD.createFromText(this, genTableName(), schema, location, split);
	}
	
	public ZDD fromTable(String name){
		return ZDD.createFromTable(this, name);
	}
	
	public ZDD fromSql(String name, String sql){
		return ZDD.createFromSql(this, name, sql);
	}
	
	public ZDD fromSql(String sql){
		return ZDD.createFromSql(this, genTableName(), sql);
	}
	
	public ZDD fromRDD(String name, Schema schema, RDD rdd){
		return ZDD.createFromRdd(this, name, schema, rdd);
	}
	

}
