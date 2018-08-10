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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import org.apache.log4j.Logger;
import org.apache.zeppelin.rest.message.LoggerRequest;
import org.apache.zeppelin.rest.message.gson.LoggerSerializer;

@Provider
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GsonProvider<T> implements MessageBodyReader<T>, MessageBodyWriter<T> {
  private final Gson gson;

  public GsonProvider() {
    GsonBuilder gsonBuilder = new GsonBuilder().enableComplexMapKeySerialization();
    gsonBuilder.registerTypeAdapter(Logger.class, new LoggerSerializer());
    this.gson = gsonBuilder.create();
  }

  @Override
  public boolean isReadable(
      Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return type == LoggerRequest.class; // For backward compatibility
  }

  @Override
  public T readFrom(
      Class<T> type,
      Type genericType,
      Annotation[] annotations,
      MediaType mediaType,
      MultivaluedMap<String, String> httpHeaders,
      InputStream entityStream)
      throws IOException, WebApplicationException {
    return gson.fromJson(new BufferedReader(new InputStreamReader(entityStream)), type);
  }

  @Override
  public boolean isWriteable(
      Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return type != String.class; // Keep backward compatibility
  }

  @Override
  public void writeTo(
      T t,
      Class<?> type,
      Type genericType,
      Annotation[] annotations,
      MediaType mediaType,
      MultivaluedMap<String, Object> httpHeaders,
      OutputStream entityStream)
      throws IOException, WebApplicationException {
    try (PrintWriter printWriter = new PrintWriter(entityStream)) {
      printWriter.write(gson.toJson(t));
      printWriter.flush();
    }
  }
}
