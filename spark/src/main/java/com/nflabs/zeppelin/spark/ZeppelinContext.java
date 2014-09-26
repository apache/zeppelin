package com.nflabs.zeppelin.spark;

import java.util.Iterator;

import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SchemaRDD;

import scala.Tuple2;

import com.nflabs.zeppelin.notebook.form.Input.ParamOption;
import com.nflabs.zeppelin.notebook.form.Setting;
import com.nflabs.zeppelin.spark.dep.DependencyResolver;

public class ZeppelinContext {
	private DependencyResolver dep;
	public ZeppelinContext(SparkContext sc, SQLContext sql, DependencyResolver dep) {
		this.sc = sc;
		this.sqlContext = sql;
		this.dep = dep;
	}
	public SparkContext sc;
	public SQLContext sqlContext;
	private Setting form;
	
	public SchemaRDD sql(String sql) {
		return sqlContext.sql(sql);
	}
	
	/**
	 * Load dependency for interpreter and runtime (driver) 
	 * @param artifact "group:artifact:version"
	 * @throws Exception 
	 */
	public void load(String artifact) throws Exception{
		dep.load(artifact, false, false);
	}

	/**
	 * Load dependency for interpreter and runtime (driver) 
	 * @param artifact "group:artifact:version"
	 * @throws Exception 
	 */
	public void load(String artifact, boolean recursive) throws Exception{
		dep.load(artifact, recursive, false);
	}	
	
	/**
	 * Load dependency for interpreter and runtime, and then add to sparkContext
	 * @throws Exception 
	 */
	public void loadAndDist(String artifact) throws Exception{
		dep.load(artifact, false, true);
	}
	
	public void loadAndDist(String artifact, boolean recursive) throws Exception{
		dep.load(artifact, true, true);
	}	
	
	/**
	 * Load dependency only interpreter
	 * @param name
	 * @return
	 */

	public Object input(String name) {
		return input(name, "");
	}
	
	public Object input(String name, Object defaultValue) {
		return form.input(name, defaultValue);
	}
	
	public Object select(String name, scala.collection.Iterable<Tuple2<Object, String>> options) {
		return select(name, "", options);
	}
	
	public Object select(String name, Object defaultValue, scala.collection.Iterable<Tuple2<Object, String>> options) {
		int n = options.size();
		ParamOption [] paramOptions = new ParamOption[n];
		Iterator<Tuple2<Object, String>> it = scala.collection.JavaConversions.asJavaIterable(options).iterator();

		int i=0;
		while (it.hasNext()) {
			Tuple2<Object, String> valueAndDisplayValue = it.next();
			paramOptions[i++] = new ParamOption(valueAndDisplayValue._1(), valueAndDisplayValue._2());
		}
		
		return form.select(name, "", paramOptions);
	}
	
	public void setFormSetting(Setting o) {
		this.form = o;	
	}
}
