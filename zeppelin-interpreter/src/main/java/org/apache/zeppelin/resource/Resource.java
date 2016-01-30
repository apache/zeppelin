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

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Information and reference to the resource
 */
public class Resource {
  private final transient Object r;
  private final boolean serializable;
  private final ResourceId resourceId;
  private final String className;


  /**
   * Create local resource
   * @param resourceId
   * @param r must not be null
   */
  Resource(ResourceId resourceId, Object r) {
    this.r = r;
    this.resourceId = resourceId;
    this.serializable = r instanceof Serializable;
    this.className = r.getClass().getName();
  }

  /**
   * Create remote object
   * @param resourceId
   */
  Resource(ResourceId resourceId, boolean serializable, String className) {
    this.r = null;
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
   *
   * @return null when this is remote resource and not serializable.
   */
  public Object get() {
    if (isLocal() || isSerializable()){
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
   * @return
   */
  public boolean isRemote() {
    return !isLocal();
  }

  /**
   * Whether it is locally accessible or not
   * @return
   */
  public boolean isLocal() {
    return true;
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

}
