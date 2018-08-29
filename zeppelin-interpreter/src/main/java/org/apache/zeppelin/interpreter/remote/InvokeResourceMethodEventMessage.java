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
package org.apache.zeppelin.interpreter.remote;

import com.google.gson.Gson;
import org.apache.zeppelin.common.JsonSerializable;
import org.apache.zeppelin.resource.ResourceId;

/**
 * message payload to invoke method of resource in the resourcepool
 */
public class InvokeResourceMethodEventMessage implements JsonSerializable {
  private static final Gson gson = new Gson();

  public final ResourceId resourceId;
  public final String methodName;
  public final String[] paramClassnames;
  public final Object[] params;
  public final String returnResourceName;

  public InvokeResourceMethodEventMessage(
      ResourceId resourceId,
      String methodName,
      Class[] paramtypes,
      Object[] params,
      String returnResourceName
  ) {
    this.resourceId = resourceId;
    this.methodName = methodName;
    if (paramtypes != null) {
      paramClassnames = new String[paramtypes.length];
      for (int i = 0; i < paramClassnames.length; i++) {
        paramClassnames[i] = paramtypes[i].getName();
      }
    } else {
      paramClassnames = null;
    }

    this.params = params;
    this.returnResourceName = returnResourceName;
  }

  public Class [] getParamTypes() throws ClassNotFoundException {
    if (paramClassnames == null) {
      return null;
    }

    Class [] types = new Class[paramClassnames.length];
    for (int i = 0; i < paramClassnames.length; i++) {
      types[i] = this.getClass().getClassLoader().loadClass(paramClassnames[i]);
    }

    return types;
  }

  public boolean shouldPutResultIntoResourcePool() {
    return (returnResourceName != null);
  }

  @Override
  public int hashCode() {
    String hash = resourceId.hashCode() + methodName;
    if (paramClassnames != null) {
      for (String name : paramClassnames) {
        hash += name;
      }
    }
    if (returnResourceName != null) {
      hash += returnResourceName;
    }

    return hash.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof InvokeResourceMethodEventMessage) {
      InvokeResourceMethodEventMessage r = (InvokeResourceMethodEventMessage) o;
      return r.hashCode() == hashCode();
    } else {
      return false;
    }
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static InvokeResourceMethodEventMessage fromJson(String json) {
    return gson.fromJson(json, InvokeResourceMethodEventMessage.class);
  }
}
