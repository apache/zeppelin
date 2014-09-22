package com.nflabs.zeppelin.spark;

import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SchemaRDD;

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
	 * Load dependency
	 * @param artifact "group:artifact:version"
	 */
	public void load(String artifact){
		dep.load(artifact, false);
	}
	
	/**
	 * Load dependency with recursion
	 */
	public void loadR(String artifact){
		dep.load(artifact, true);
	}

	public Object input(String name) {
		return input(name, "");
	}
	
	public Object input(String name, Object defaultValue) {
		return form.input(name, defaultValue);
	}
	
	public void setFormSetting(Setting o) {
		this.form = o;	
	}
}
