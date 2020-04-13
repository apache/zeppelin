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
package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import org.apache.zeppelin.jupyter.types.JupyterOutputType;
import org.apache.zeppelin.jupyter.types.ZeppelinOutputType;
import org.apache.zeppelin.jupyter.zformat.TypeData;

import java.util.Map;

/**
 *
 */
public class ExecuteResult extends Output {

  /**
   * TODO(zjffdu) not sure why I have to use String here, otherwise Gson will throw NumberFormatException
   * even when the data format is correct.
   * The data in json file is "22", but somehow I see "22.0" when exception happens
   * com.google.gson.JsonSyntaxException: java.lang.NumberFormatException: For input string: "22.0"
   *
   * 	at com.google.gson.internal.bind.TypeAdapters$7.read(TypeAdapters.java:232)
   * 	at com.google.gson.internal.bind.TypeAdapters$7.read(TypeAdapters.java:222)
   * 	at com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$1.read(ReflectiveTypeAdapterFactory.java:93)
   * 	at com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$Adapter.read(ReflectiveTypeAdapterFactory.java:172)
   * 	at com.google.gson.TypeAdapter.fromJsonTree(TypeAdapter.java:281)
   * 	at com.google.gson.typeadapters.RuntimeTypeAdapterFactory$1.read(RuntimeTypeAdapterFactory.java:214)
   */
  @SerializedName("execution_count")
  private String executionCount;

  @SerializedName("data")
  private Map<String, Object> data;

  public Map<String, Object> getData() {
    return data;
  }

  @Override
  public ZeppelinOutputType getTypeOfZeppelin() {
    return getType(data).getZeppelinType();
  }

  @Override
  public TypeData toZeppelinResult() {
    return getZeppelinResult(data, getType(data));
  }
}
