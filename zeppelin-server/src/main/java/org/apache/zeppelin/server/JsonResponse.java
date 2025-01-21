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
package org.apache.zeppelin.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;

import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response.ResponseBuilder;

/**
 * Json response builder.
 *
 * @param <T>
 */
public class JsonResponse<T> {
  private jakarta.ws.rs.core.Response.Status status;
  private String message;
  private T body;
  transient ArrayList<NewCookie> cookies;
  transient boolean pretty = false;

  public JsonResponse(jakarta.ws.rs.core.Response.Status status) {
    this.status = status;
    this.message = null;
    this.body = null;
  }

  public JsonResponse(jakarta.ws.rs.core.Response.Status status, String message) {
    this.status = status;
    this.message = message;
    this.body = null;
  }

  public JsonResponse(jakarta.ws.rs.core.Response.Status status, T body) {
    this.status = status;
    this.message = null;
    this.body = body;
  }

  public JsonResponse(jakarta.ws.rs.core.Response.Status status, String message, T body) {
    this.status = status;
    this.message = message;
    this.body = body;
  }

  public JsonResponse<T> setPretty(boolean pretty) {
    this.pretty = pretty;
    return this;
  }

  /**
   * Add cookie for building.
   *
   * @param newCookie
   * @return
   */
  public JsonResponse<T> addCookie(NewCookie newCookie) {
    if (cookies == null) {
      cookies = new ArrayList<>();
    }
    cookies.add(newCookie);

    return this;
  }

  /**
   * Add cookie for building.
   *
   * @param name
   * @param value
   * @return
   */
  public JsonResponse<?> addCookie(String name, String value) {
    return addCookie(new NewCookie.Builder(name).value(value).build());
  }

  @Override
  public String toString() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    if (pretty) {
      gsonBuilder.setPrettyPrinting();
    }
    gsonBuilder.setExclusionStrategies(new JsonExclusionStrategy());
    Gson gson = gsonBuilder.create();
    return gson.toJson(this);
  }

  public jakarta.ws.rs.core.Response.Status getCode() {
    return status;
  }

  public void setCode(jakarta.ws.rs.core.Response.Status status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public T getBody() {
    return body;
  }

  public void setBody(T body) {
    this.body = body;
  }

  public jakarta.ws.rs.core.Response build() {
    ResponseBuilder r = jakarta.ws.rs.core.Response.status(status).entity(this.toString());
    if (cookies != null) {
      for (NewCookie nc : cookies) {
        r.cookie(nc);
      }
    }
    return r.build();
  }
}
