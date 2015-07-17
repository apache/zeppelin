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

package org.apache.zeppelin.display;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * AngularObjectRegistry keeps all the object that binded to Angular Display System.
 * AngularObjectRegistry is created per interpreter group.
 * It keeps two different set of AngularObjects :
 *  - globalRegistry: Shared to all notebook that uses the same interpreter group
 *  - localRegistry: AngularObject is valid only inside of a single notebook
 */
public class AngularObjectRegistry {
  Map<String, Map<String, AngularObject>> registry = 
      new HashMap<String, Map<String, AngularObject>>();
  private final String GLOBAL_KEY = "_GLOBAL_";
  private AngularObjectRegistryListener listener;
  private String interpreterId;
  

  AngularObjectListener angularObjectListener;

  public AngularObjectRegistry(final String interpreterId,
      final AngularObjectRegistryListener listener) {
    this.interpreterId = interpreterId;
    this.listener = listener;
    angularObjectListener = new AngularObjectListener() {
      @Override
      public void updated(AngularObject updatedObject) {
        if (listener != null) {
          listener.onUpdate(interpreterId, updatedObject);
        }
      }
    };
  }

  public AngularObjectRegistryListener getListener() {
    return listener;
  }

  /**
   * Add object into registry
   * @param name
   * @param o
   * @param noteId noteId belonging to. null for global object.
   * @return
   */
  public AngularObject add(String name, Object o, String noteId) {
    return add(name, o, noteId, true);
  }

  private String getRegistryKey(String noteId) {
    if (noteId == null) {
      return GLOBAL_KEY;
    } else {
      return noteId;
    }
  }
  
  private Map<String, AngularObject> getRegistryForKey(String noteId) {
    synchronized (registry) {
      String key = getRegistryKey(noteId);
      if (!registry.containsKey(key)) {
        registry.put(key, new HashMap<String, AngularObject>());
      }
      
      return registry.get(key);
    }
  }
 
  public AngularObject add(String name, Object o, String noteId, boolean emit) {
    AngularObject ao = createNewAngularObject(name, o, noteId);

    synchronized (registry) {
      Map<String, AngularObject> noteLocalRegistry = getRegistryForKey(noteId);
      noteLocalRegistry.put(name, ao);
      if (listener != null && emit) {
        listener.onAdd(interpreterId, ao);
      }
    }

    return ao;
  }

  protected AngularObject createNewAngularObject(String name, Object o, String noteId) {
    return new AngularObject(name, o, noteId, angularObjectListener);
  }

  protected AngularObjectListener getAngularObjectListener() {
    return angularObjectListener;
  }

  public AngularObject remove(String name, String noteId) {
    return remove(name, noteId, true);
  }

  public AngularObject remove(String name, String noteId, boolean emit) {
    synchronized (registry) {
      Map<String, AngularObject> r = getRegistryForKey(noteId);
      AngularObject o = r.remove(name);
      if (listener != null && emit) {
        listener.onRemove(interpreterId, name, noteId);;
      }
      return o;
    }
  }

  public void removeAll(String noteId) {
    synchronized (registry) {
      List<AngularObject> all = getAll(noteId);
      for (AngularObject ao : all) {
        remove(ao.getName(), noteId);
      }
    }
  }

  public AngularObject get(String name, String noteId) {
    synchronized (registry) {
      Map<String, AngularObject> r = getRegistryForKey(noteId);
      return r.get(name);
    }
  }

  public List<AngularObject> getAll(String noteId) {
    List<AngularObject> all = new LinkedList<AngularObject>();
    synchronized (registry) {
      Map<String, AngularObject> r = getRegistryForKey(noteId);
      if (r != null) {
        all.addAll(r.values());
      }
    }
    return all;
  }
  
  /**
   * Get all object with global merged
   * @param noteId
   * @return
   */
  public List<AngularObject> getAllWithGlobal(String noteId) {
    List<AngularObject> all = new LinkedList<AngularObject>();
    synchronized (registry) {
      Map<String, AngularObject> global = getRegistryForKey(null);
      if (global != null) {
        all.addAll(global.values());
      }
      Map<String, AngularObject> local = getRegistryForKey(noteId);
      if (local != null) {
        all.addAll(local.values());
      }
    }
    return all;
  }

  public String getInterpreterGroupId() {
    return interpreterId;
  }
}
