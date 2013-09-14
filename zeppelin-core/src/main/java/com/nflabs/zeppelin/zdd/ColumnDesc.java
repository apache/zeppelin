/*
 * Copyright (C) 2012 The Regents of The University California.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nflabs.zeppelin.zdd;

import java.io.Serializable;
import java.util.List;

import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Schema;

import com.nflabs.zeppelin.zdd.DataTypes.UnknownDataTypeException;

public class ColumnDesc implements Serializable {
	String name;
	DataType dataType;

	public ColumnDesc(String name, DataType dataType){
		this.name = name;
		this.dataType = dataType;
	}
	
	public ColumnDesc(shark.api.ColumnDesc desc){
		this(desc.name(), new DataType(desc.dataType()));
	}
	
	
	public String name(){
		return name;
	}
	
	public DataType type(){
		return dataType;
	}
	
	public ColumnDesc(FieldSchema hiveSchema) throws UnknownDataTypeException{
		this(hiveSchema.getName(), DataTypes.fromHiveType(hiveSchema.getType()));
	}
	
	public static ColumnDesc [] createSchema(shark.api.ColumnDesc [] cols){
		if(cols==null) return new ColumnDesc[]{};
		else {
			ColumnDesc [] desc = new ColumnDesc[cols.length];
			int i=0; 
			for(shark.api.ColumnDesc d : cols){
				desc[i++] = new ColumnDesc(d);
			}
			return desc;
		}
	}
	
	public static ColumnDesc [] createSchema(List<FieldSchema> fieldSchemas) throws UnknownDataTypeException{
		if(fieldSchemas == null){
			return new ColumnDesc[]{};
		} else {
			ColumnDesc[] desc = new ColumnDesc[fieldSchemas.size()];
			int i=0;
			for(FieldSchema f : fieldSchemas){
				desc[i++]=new ColumnDesc(f);
			}
			return desc;
		}
	}
	
	public static ColumnDesc [] createSchema(Schema schema) throws UnknownDataTypeException{
		if(schema == null){
			return new ColumnDesc[] {};
		} else {
			return createSchema(schema.getFieldSchemas());
		}
	}
	
	
}

