package com.nflabs.zeppelin.zdd;


import java.net.URI;
import java.util.logging.Logger;

import org.mortbay.log.Log;

import com.nflabs.zeppelin.Zeppelin;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;
import com.nflabs.zeppelin.zrt.ZeppelinRuntimeException;

import shark.api.TableRDD;
/**
 * ZDD is lazy evaluated data representation
 * 
 * 
 * @author moon
 *
 */
public class ZDD {
	Logger logger = Logger.getLogger(ZDD.class.getName());
	
	private ZeppelinRuntime runtime;
	private Source src;
	
	// when zdd is evaluated, than name, schema, rdd will be set
	private String name;
	private Schema schema;
	private TableRDD rdd;
	
	
	// source text
	private String location;  // directory
	char split;
	
	
	// source sql
	private String sql;
	
	
	private static enum Source{
		TableRDD,
		Text,		
		SQL,
		Table
	}
	
	private ZDD(ZeppelinRuntime runtime, Source src, String name){
		this.runtime = runtime;
		this.src = src;
		this.name = name;
	}
	
	/**
	 * Create ZDD from text file
	 * @param runtime
	 * @param name
	 * @param schema
	 * @param location
	 * @param split
	 * @return
	 */
	public static ZDD createFromText(ZeppelinRuntime runtime, String name, Schema schema, String location, char split){
		ZDD zdd = new ZDD(runtime, Source.Text, name);
		zdd.location = location;
		zdd.schema = schema;
		zdd.split = split;
		return zdd;
	}
	
	/**
	 * Create ZDD from existing table
	 * @param runtime
	 * @param table
	 * @return
	 */
	public static ZDD createFromTable(ZeppelinRuntime runtime, String table){
		ZDD zdd = new ZDD(runtime, Source.Table, table);
		return zdd;
	}
	
	/**
	 * Create ZDD from sql
	 * @param runtime
	 * @param name
	 * @param sql
	 * @return
	 */
	public static ZDD createFromSql(ZeppelinRuntime runtime, String name, String sql){
		ZDD zdd = new ZDD(runtime, Source.SQL, name);
		zdd.sql = sql;
		return zdd;
	}
	
	
	public Schema schema() throws ZeppelinRuntimeException{
		evaluate();
		return schema;
	}
	
	public TableRDD rdd() throws ZeppelinRuntimeException{
		evaluate();
		return rdd;
	}
	
	
	public boolean equals(Object o){
		if(o instanceof ZDD){
			return name().equals(((ZDD)o).name());
		} else {
			return false;
		}
	}
	
	public void saveAsTextFile(String path){
		rdd.saveAsTextFile(path);
	}
	
	public boolean isEvaluated(){
		if(name!=null && rdd!=null && schema!=null){
			return true;
		} else {
			return false;
		}
	}
	
	public void evaluate() throws ZeppelinRuntimeException{
		if(isEvaluated()==true) return;
		if(src==Source.Text){
			String tc = 
					"CREATE EXTERNAL TABLE "+ name+
					"("+schema.toHiveTableCreationQueryColumnPart()+") "+
					"ROW FORMAT DELIMITED FIELDS TERMINATED BY '"+split+"' "+
					"STORED AS TEXTFILE " +
					"LOCATION '"+location.toString()+"'"				
					;
			Log.debug("execute sql "+tc);
			try{
				runtime.sql(tc);
				rdd = runtime.sql2rdd("select * from "+name);
				rdd.setName(name);
			} catch(Exception e){
				throw new ZeppelinRuntimeException(e);
			}
		} else if(src==Source.SQL){
			try{
				runtime.sql("CREATE VIEW "+name+" as "+sql);
				rdd = runtime.sql2rdd("select * from "+name);
				rdd.setName(name);
				schema = new Schema(ColumnDesc.createSchema(rdd.schema()));
			} catch(Exception e){
				throw new ZeppelinRuntimeException(e);
			}
		} else if(src==Source.Table){
			try{
				rdd = runtime.sql2rdd("select * from "+name);
				rdd.setName(name);
				schema = new Schema(ColumnDesc.createSchema(rdd.schema()));
			} catch(Exception e){
				throw new ZeppelinRuntimeException(e);
			}
		}
	}
	
	public void destroy() throws ZeppelinRuntimeException{
		if(isEvaluated()==false) return;
		
		if(src==Source.Text){
			runtime.sql("DROP TABLE IF EXISTS "+name);
		} else if(src==Source.SQL){
			runtime.sql("DROP VIEW IF EXISTS "+name);
		} else if(src==Source.Table){
			// nothing to do
		}
	}

	public String name() {
		return name;
	}
	public Source src(){
		return src;
	}
}
