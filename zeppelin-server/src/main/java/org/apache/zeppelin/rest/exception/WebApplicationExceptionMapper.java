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

package org.apache.zeppelin.rest.exception;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.apache.zeppelin.rest.message.gson.ExceptionSerializer;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
  private final Gson gson;

  public WebApplicationExceptionMapper() {
    GsonBuilder gsonBuilder = new GsonBuilder().enableComplexMapKeySerialization();
    gsonBuilder.registerTypeHierarchyAdapter(
        Exception.class, new ExceptionSerializer());
    this.gson = gsonBuilder.create();
  }

  @Override
  public Response toResponse(WebApplicationException exception) {
    return Response.status(exception.getResponse().getStatus())
        .entity(gson.toJson(exception)).build();
  }
}
