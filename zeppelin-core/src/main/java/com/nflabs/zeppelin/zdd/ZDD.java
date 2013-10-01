package com.nflabs.zeppelin.zdd;


import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.JavaStringObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.rdd.RDD;




import com.nflabs.zeppelin.Zeppelin;
import com.nflabs.zeppelin.shark.RDDTableFunctions;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;
import com.nflabs.zeppelin.zrt.ZeppelinRuntimeException;

import scala.collection.JavaConversions;


import shark.SharkEnv;
import shark.api.TableRDD;
import shark.execution.EmptyRDD;
import shark.memstore2.TablePartitionBuilder;
import shark.memstore2.TablePartitionStats;
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
	private TableRDD tableRDD;
	
	
	// source text
	private String location;  // directory
	char split;
	
	
	// source sql
	private String sql;

	// source rdd
	private RDD rdd;
	
	
	private static enum Source{
		Text,		
		SQL,
		Table,
		RDD,
		Array,
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
	
	public static ZDD createFromRdd(ZeppelinRuntime runtime, String name, Schema schema, RDD rdd){
		ZDD zdd = new ZDD(runtime, Source.Table, name);
		zdd.schema = schema;
		zdd.rdd = rdd;
		return zdd;
	}
	
	
	public String getLocation() throws ZeppelinRuntimeException{
		if(src==Source.Text){
			return location;
		} else if(src==Source.Table){
			List<String> ret = runtime.sql("describe formatted "+name);
			Pattern p = Pattern.compile("^Location:+\\s(.*)");
			for(String s: ret){
				Matcher m = p.matcher(s);
				if(m.matches()==false) continue;
				return m.group(1).trim();
			}
			throw new ZeppelinRuntimeException("Can not get location of the table '"+name+"'");
		} else {
			throw new ZeppelinRuntimeException("Not implemented");
		} 
	}
	

	
	public Schema schema() throws ZeppelinRuntimeException{
		if(schema==null){
			evaluate();
		}
		return schema;
	}
	
	public TableRDD tableRdd() throws ZeppelinRuntimeException{
		if(tableRDD==null){
			evaluate();
		}
		return tableRDD;
	}
	
	public RDD rdd() throws ZeppelinRuntimeException{
		if(rdd!=null){
			return rdd;
		} else {
			return tableRdd();
		}
	}
	
	
	public boolean equals(Object o){
		if(o instanceof ZDD){
			return name().equals(((ZDD)o).name());
		} else {
			return false;
		}
	}
	
	public boolean isEvaluated(){
		if(name!=null && tableRDD!=null && schema!=null){
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
			
			logger.info("Execute "+tc);
			
			try{
				runtime.sql(tc);
				tableRDD = runtime.sql2rdd("select * from "+name);
				tableRDD.setName(name);
			} catch(Exception e){
				throw new ZeppelinRuntimeException(e);
			}
		} else if(src==Source.SQL){
			try{
				runtime.sql("CREATE VIEW "+name+" as "+sql);
				tableRDD = runtime.sql2rdd("select * from "+name);
				tableRDD.setName(name);
				schema = new Schema(ColumnDesc.createSchema(tableRDD.schema()));
			} catch(Exception e){
				throw new ZeppelinRuntimeException(e);
			}
		} else if(src==Source.Table){
			try{
				tableRDD = runtime.sql2rdd("select * from "+name);
				tableRDD.setName(name);
				schema = new Schema(ColumnDesc.createSchema(tableRDD.schema()));
			} catch(Exception e){
				throw new ZeppelinRuntimeException(e);
			}
		} else if(src==Source.RDD){
			try{
				/*
				List<ObjectInspector> columnOIs = new ArrayList<ObjectInspector>(schema.getColumns().length);
				for(int i=0; i<schema.getColumns().length; i++){
					ColumnDesc col = schema.getColumns()[i];
					ObjectInspector oi = null;
					if(col.type().name.equals(DataTypes.STRING.name)){
						oi = PrimitiveObjectInspectorFactory.javaStringObjectInspector;
					} else if(col.type().name.equals(DataTypes.INT)){
						oi = PrimitiveObjectInspectorFactory.javaIntObjectInspector;
					}
					columnOIs.add(i, oi);
				}
			
			    val statsAcc = SharkEnv.sc().accumulableCollection(arg0, arg1)

				ClassManifest m = rdd.elementClassManifest();
				Class[] classes = m.erasure().getClasses();
				
				
				SharkEnv.memoryMetadataManager().putStats(key, stats)

				
				Map<Object, TablePartitionStats> stats = new HashMap<Object, TablePartitionStats>();
				List<String> fields = new LinkedList<String>();

				for(ColumnDesc c : schema.getColumns()){
					fields.add(c.name());
				}

				List<Class> manifests = new LinkedList<Class>();
				*/				
				
				//scala.collection.immutable.<ClassManifest> a = JavaConversions.asScalaBuffer(manifests).toList();
				
				
				ColumnDesc.convertToSharkColumnDesc(schema.getColumns());
				List<shark.api.DataType> types = new LinkedList<shark.api.DataType>();
				List<String> fields = new LinkedList<String>();
				for(ColumnDesc c : schema.getColumns()){
					fields.add(c.name());
					types.add(c.type().toSharkType());
				}
				if(new RDDTableFunctions(rdd, JavaConversions.asScalaBuffer(types).toList())
				    .saveAsTable(name, JavaConversions.asScalaBuffer(fields).toList())){
					tableRDD = runtime.sql2rdd("select * from "+name);
					tableRDD.setName(name);
				} else {
					throw new ZeppelinRuntimeException("Can't convert");
				}
				
				tableRDD = runtime.sql2rdd("select * from "+name);
				tableRDD.setName(name);

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
	
	public void drop() throws ZeppelinRuntimeException{
		destroy();
	}

	public String name() {
		return name;
	}
	public Source src(){
		return src;
	}
}
