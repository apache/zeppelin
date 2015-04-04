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

package com.nflabs.zeppelin.display;

import java.util.HashMap;
import java.util.Map;

/**
 *
 *
 */
public class AngularObjectRegistry implements AngularObjectListener {
  Map<String, AngularObject> registry = new HashMap<String, AngularObject>();
  private AngularObjectRegistryListener listener;
  private String interpreterId;

  public AngularObjectRegistry(String interpreterId,
      AngularObjectRegistryListener listener) {
    this.interpreterId = interpreterId;
    this.listener = listener;

  }

  public AngularObjectRegistryListener getListener() {
    return listener;
  }

  public AngularObject add(String name, Object o) {
    AngularObject ao = createNewAngularObject(name, o);

    synchronized (registry) {
      registry.put(name, ao);
      if (listener != null) {
        listener.onAdd(interpreterId, ao);
      }
    }

    return ao;
  }

  protected AngularObject createNewAngularObject(String name, Object o) {
    return new AngularObject(name, o, this);
  }

  public AngularObject remove(String name) {
    synchronized (registry) {
      AngularObject o = registry.remove(name);
      if (listener != null) {
        listener.onRemove(interpreterId, o);;
      }
      return o;
    }
  }

  public AngularObject get(String name) {
    synchronized (registry) {
      return registry.get(name);
    }
  }

  @Override
  public void updated(AngularObject updatedObject) {
    if (listener != null) {
      listener.onUpdate(interpreterId, updatedObject);
    }
  }

  public String getInterpreterGroupId() {
    return interpreterId;
  }
}
