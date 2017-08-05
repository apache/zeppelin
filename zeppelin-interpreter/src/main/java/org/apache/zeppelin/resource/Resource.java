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
package org.apache.zeppelin.resource;

import com.google.gson.Gson;
import org.apache.zeppelin.common.JsonSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Information and reference to the resource
 */
public class Resource implements JsonSerializable {
  private static final Gson gson = new Gson();

  private final transient Object r;
  private final transient LocalResourcePool pool;
  private final boolean serializable;
  private final ResourceId resourceId;
  private final String className;


  /**
   * Create local resource
   *
   * @param resourceId
   * @param r          must not be null
   */
  Resource(LocalResourcePool pool, ResourceId resourceId, Object r) {
    this.r = r;
    this.pool = pool;
    this.resourceId = resourceId;
    this.serializable = r instanceof Serializable;
    this.className = r.getClass().getName();
  }

  /**
   * Create remote object
   *
   * @param resourceId
   */
  Resource(LocalResourcePool pool, ResourceId resourceId, boolean serializable, String className) {
    this.r = null;
    this.pool = pool;
    this.resourceId = resourceId;
    this.serializable = serializable;
    this.className = className;
  }

  public ResourceId getResourceId() {
    return resourceId;
  }

  public String getClassName() {
    return className;
  }

  /**
   * @return null when this is remote resource and not serializable.
   */
  public Object get() {
    if (isLocal() || isSerializable()) {
      return r;
    } else {
      return null;
    }
  }

  public boolean isSerializable() {
    return serializable;
  }

  /**
   * if it is remote object
   *
   * @return
   */
  public boolean isRemote() {
    return !isLocal();
  }

  /**
   * Whether it is locally accessible or not
   *
   * @return
   */
  public boolean isLocal() {
    return true;
  }


  /**
   * Call a method of the object that this resource holds
   * @param methodName name of method to call
   * @param paramTypes method parameter types
   * @param params method parameter values
   * @return return value of the method
   */
  public Object invokeMethod(
      String methodName, Class [] paramTypes, Object [] params) {
    if (r != null) {
      try {
        Method method = r.getClass().getMethod(
            methodName,
            paramTypes);
        method.setAccessible(true);
        Object ret = method.invoke(r, params);
        return ret;
      }  catch (Exception e) {
        logException(e);
        return null;
      }
    } else {
      return null;
    }
  }

  /**
   * Call a method of the object that this resource holds and save return value as a resource
   * @param methodName name of method to call
   * @param paramTypes method parameter types
   * @param params method parameter values
   * @param returnResourceName name of resource that return value will be saved
   * @return Resource that holds return value
   */
  public Resource invokeMethod(
      String methodName, Class [] paramTypes, Object [] params, String returnResourceName) {
    if (r != null) {
      try {
        Method method = r.getClass().getMethod(
            methodName,
            paramTypes);
        Object ret = method.invoke(r, params);
        pool.put(
            resourceId.getNoteId(),
            resourceId.getParagraphId(),
            returnResourceName,
            ret
        );
        return pool.get(
            resourceId.getNoteId(),
            resourceId.getParagraphId(),
            returnResourceName);
      } catch (Exception e) {
        logException(e);
        return null;
      }
    } else {
      return null;
    }
  }

  public static ByteBuffer serializeObject(Object o) throws IOException {
    if (o == null || !(o instanceof Serializable)) {
      return null;
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ObjectOutputStream oos;
      oos = new ObjectOutputStream(out);
      oos.writeObject(o);
      oos.close();
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return ByteBuffer.wrap(out.toByteArray());
  }

  public static Object deserializeObject(ByteBuffer buf)
      throws IOException, ClassNotFoundException {
    if (buf == null) {
      return null;
    }
    InputStream ins = ByteBufferInputStream.get(buf);
    ObjectInputStream oin;
    Object object = null;

    oin = new ObjectInputStream(ins);
    object = oin.readObject();
    oin.close();
    ins.close();

    return object;
  }

  private void logException(Exception e) {
    Logger logger = LoggerFactory.getLogger(Resource.class);
    logger.error(e.getMessage(), e);
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static Resource fromJson(String json) {
    return gson.fromJson(json, Resource.class);
  }
}
