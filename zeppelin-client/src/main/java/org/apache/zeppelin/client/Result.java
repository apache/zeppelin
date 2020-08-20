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


package org.apache.zeppelin.client;

import kong.unirest.json.JSONObject;

/**
 * Represent one segment of result of paragraph. The result of paragraph could consists of
 * multiple Results.
 */
public class Result {
  private String type;
  private String data;

  public Result(JSONObject jsonObject) {
    this.type = jsonObject.getString("type");
    this.data = jsonObject.getString("data");
  }

  public Result(String type, String data) {
    this.type = type;
    this.data = data;
  }

  public String getType() {
    return type;
  }

  public String getData() {
    return data;
  }

  public void appendData(String newData) {
    this.data = this.data + newData;
  }

  @Override
  public String toString() {
    return "Result{" +
            "type='" + type + '\'' +
            ", data='" + data + '\'' +
            '}';
  }
}
