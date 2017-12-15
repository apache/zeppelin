/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.kylin;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.zeppelin.common.JsonSerializable;

/**
 * class for Kylin Error Response.
 */
class KylinErrorResponse implements JsonSerializable {
  private static final Gson gson = new Gson();

  private String stacktrace;
  private String exception;
  private String url;
  private String code;
  private Object data;
  private String msg;

  public KylinErrorResponse(String stacktrace, String exception, String url,
      String code, Object data, String msg) {
    this.stacktrace = stacktrace;
    this.exception = exception;
    this.url = url;
    this.code = code;
    this.data = data;
    this.msg = msg;
  }

  public String getException() {
    return exception;
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static KylinErrorResponse fromJson(String json) {
    try {
      return gson.fromJson(json, KylinErrorResponse.class);
    } catch (JsonSyntaxException ex) {
      return null;
    }
  }

}
