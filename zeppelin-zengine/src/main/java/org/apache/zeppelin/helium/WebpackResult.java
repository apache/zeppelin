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
package org.apache.zeppelin.helium;

import com.google.gson.Gson;
import org.apache.zeppelin.common.JsonSerializable;

/** Represetns webpack json format result */
public class WebpackResult implements JsonSerializable {
  private static final Gson gson = new Gson();

  public final String[] errors = new String[0];
  public final String[] warnings = new String[0];

  public String toJson() {
    return gson.toJson(this);
  }

  public static WebpackResult fromJson(String json) {
    return gson.fromJson(json, WebpackResult.class);
  }
}
