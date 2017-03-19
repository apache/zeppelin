/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.groovy;


import java.io.StringWriter;
import org.slf4j.Logger;
import java.util.Properties;
import java.util.Collection;

import groovy.xml.MarkupBuilder;
import groovy.lang.Closure;

import org.apache.zeppelin.interpreter.InterpreterContext;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObject;

/**
 * Groovy interpreter for Zeppelin.
 */
public class GObject extends groovy.lang.GroovyObjectSupport {
	Logger log;
	StringWriter out;
	Properties props;
	InterpreterContext interpreterContext;
	
	public GObject(Logger log, StringWriter out, Properties p, InterpreterContext ctx){
		this.log=log;
		this.out=out;
		this.interpreterContext=ctx;
		this.props=p;
	}
	
	public Object getProperty(String key){
		if("log".equals(key))return log;
		return props.getProperty(key);
	}
	public void setProperty(String key, Object value){
		throw new RuntimeException("Set properties not supported: "+key+"="+value);
	}
	public Properties getProperties(){
		return props;
	}
	
	private void startOutputType(String type){
		StringBuffer sb=out.getBuffer();
		if( sb.length()>0 ){
			if( sb.length()<type.length() || !type.equals(sb.substring(0,type.length())) ){
				log.error("try to start output `"+type+"` after non-"+type+" started");
			}
		}else{
			out.append(type);
			out.append('\n');
		}
	}
	
	/**
	 * starts or continues rendering html/angular and returns MarkupBuilder to build html.
	 * <pre> g.html().with{ 
	 *	h1("hello")
	 *  h2("world")
	 * }</pre>
	 */
	public MarkupBuilder html(){
		startOutputType("%angular");
		return new MarkupBuilder(out);
	}
	
	/**
	 * starts or continues rendering table rows
	 * @param obj:  
	 *  1. List(rows) of List(columns) where first line is a header
	 */
	public void table(Object obj){
		if(obj==null)return;
		StringBuffer sb=out.getBuffer();
		startOutputType("%table");
		if(obj instanceof groovy.lang.Closure){
			//if closure run and get result collection
			obj = ((Closure)obj).call();
		}
		if(obj instanceof Collection){
			int count = 0;
			for(Object row : ((Collection)obj)){
				count++;
				boolean rowStarted = false;
				if(row instanceof Collection){
					for( Object field: ((Collection)row) ){
						if(rowStarted)sb.append('\t');
						sb.append(field);
						rowStarted=true;
					}
				}else{
					sb.append(row);
				}
				sb.append('\n');
			}
		}else{
			throw new RuntimeException("Not supported table value :"+obj.getClass());
		}
	}
	
	private AngularObject getAngularObject(String name) {
		AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
		String noteId = interpreterContext.getNoteId();
		// try get local object
		AngularObject paragraphAo = registry.get(name, noteId, interpreterContext.getParagraphId());
		AngularObject noteAo = registry.get(name, noteId, null);

		AngularObject ao = paragraphAo != null ? paragraphAo : noteAo;

		if (ao == null) {
			// then global object
			ao = registry.get(name, null, null);
		}
		return ao;
	}
	
	/**
	 * Get angular object. Look up notebook scope first and then global scope
	 * @param name variable name
	 * @return value
	 */
	public Object angular(String name) {
		AngularObject ao = getAngularObject(name);
		if (ao == null) {
			return null;
		} else {
			return ao.get();
		}
	}

	@SuppressWarnings("unchecked")
	public void angularBind(String name, Object o, String noteId) {
		AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

		if (registry.get(name, noteId, null) == null) {
			registry.add(name, o, noteId, null);
		} else {
			registry.get(name, noteId, null).set(o);
		}
	}

	/**
	 * Create angular variable in notebook scope and bind with front end Angular display system.
	 * If variable exists, it'll be overwritten.
	 * @param name name of the variable
	 * @param o value
	 */
	public void angularBind(String name, Object o) {
		angularBind(name, o, interpreterContext.getNoteId());
	}

}
