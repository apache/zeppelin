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

public class DataType implements Serializable {

	public final String name;
	public final String hiveName;
	public final boolean isPrimitive;
	
	public DataType(String name, String hiveName, boolean isPrimitive) {
	  this.name = name;
	  this.hiveName = hiveName;
	  this.isPrimitive = isPrimitive;
	}
	
	public DataType(shark.api.DataType type){
		this(type.name, type.hiveName, type.isPrimitive);
	}
	
	@Override
	public String toString() {
	  return name;
	}
}
