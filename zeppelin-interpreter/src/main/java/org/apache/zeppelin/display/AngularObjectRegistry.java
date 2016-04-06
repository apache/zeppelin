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
 * It provides three different scope of AngularObjects :
 *  - Paragraphscope : AngularObject is valid in specific paragraph
 *  - Notebook scope: AngularObject is valid in a single notebook
 *  - Global scope : Shared to all notebook that uses the same interpreter group
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
   *
   * Paragraph scope when noteId and paragraphId both not null
   * Notebook scope when paragraphId is null
   * Global scope when noteId and paragraphId both null
   *
   * @param name Name of object
   * @param o Reference to the object
   * @param noteId noteId belonging to. null for global scope
   * @param paragraphId paragraphId belongs to. null for notebook scope
   * @return AngularObject that added
   */
  public AngularObject add(String name, Object o, String noteId, String paragraphId) {
    return add(name, o, noteId, paragraphId, true);
  }

  private String getRegistryKey(String noteId, String paragraphId) {
    if (noteId == null) {
      return GLOBAL_KEY;
    } else {
      if (paragraphId == null) {
        return noteId;
      } else {
        return noteId + "_" + paragraphId;
      }
    }
  }
  
  private Map<String, AngularObject> getRegistryForKey(String noteId, String paragraphId) {
    synchronized (registry) {
      String key = getRegistryKey(noteId, paragraphId);
      if (!registry.containsKey(key)) {
        registry.put(key, new HashMap<String, AngularObject>());
      }
      
      return registry.get(key);
    }
  }

  /**
   * Add object into registry
   *
   * Paragraph scope when noteId and paragraphId both not null
   * Notebook scope when paragraphId is null
   * Global scope when noteId and paragraphId both null
   *
   * @param name Name of object
   * @param o Reference to the object
   * @param noteId noteId belonging to. null for global scope
   * @param paragraphId paragraphId belongs to. null for notebook scope
   * @param emit skip firing onAdd event on false
   * @return AngularObject that added
   */
  public AngularObject add(String name, Object o, String noteId, String paragraphId,
                           boolean emit) {
    AngularObject ao = createNewAngularObject(name, o, noteId, paragraphId);

    synchronized (registry) {
      Map<String, AngularObject> noteLocalRegistry = getRegistryForKey(noteId, paragraphId);
      noteLocalRegistry.put(name, ao);
      if (listener != null && emit) {
        listener.onAdd(interpreterId, ao);
      }
    }

    return ao;
  }

  protected AngularObject createNewAngularObject(String name, Object o, String noteId,
                                                 String paragraphId) {
    return new AngularObject(name, o, noteId, paragraphId, angularObjectListener);
  }

  protected AngularObjectListener getAngularObjectListener() {
    return angularObjectListener;
  }

  /**
   * Remove a object from registry
   *
   * @param name Name of object to remove
   * @param noteId noteId belongs to. null for global scope
   * @param paragraphId paragraphId belongs to. null for notebook scope
   * @return removed object. null if object is not found in registry
   */
  public AngularObject remove(String name, String noteId, String paragraphId) {
    return remove(name, noteId, paragraphId, true);
  }

  /**
   * Remove a object from registry
   *
   * @param name Name of object to remove
   * @param noteId noteId belongs to. null for global scope
   * @param paragraphId paragraphId belongs to. null for notebook scope
   * @param emit skip fireing onRemove event on false
   * @return removed object. null if object is not found in registry
   */
  public AngularObject remove(String name, String noteId, String paragraphId, boolean emit) {
    synchronized (registry) {
      Map<String, AngularObject> r = getRegistryForKey(noteId, paragraphId);
      AngularObject o = r.remove(name);
      if (listener != null && emit) {
        listener.onRemove(interpreterId, name, noteId, paragraphId);;
      }
      return o;
    }
  }

  /**
   * Remove all angular object in the scope.
   *
   * Remove all paragraph scope angular object when noteId and paragraphId both not null
   * Remove all notebook scope angular object when paragraphId is null
   * Remove all global scope angular objects when noteId and paragraphId both null
   *
   * @param noteId noteId
   * @param paragraphId paragraphId
   */
  public void removeAll(String noteId, String paragraphId) {
    synchronized (registry) {
      List<AngularObject> all = getAll(noteId, paragraphId);
      for (AngularObject ao : all) {
        remove(ao.getName(), noteId, paragraphId);
      }
    }
  }

  /**
   * Get a object from registry
   * @param name name of object
   * @param noteId noteId that belongs to
   * @param paragraphId paragraphId that belongs to
   * @return angularobject. null when not found
   */
  public AngularObject get(String name, String noteId, String paragraphId) {
    synchronized (registry) {
      Map<String, AngularObject> r = getRegistryForKey(noteId, paragraphId);
      return r.get(name);
    }
  }

  /**
   * Get all object in the scope
   * @param noteId noteId that belongs to
   * @param paragraphId paragraphId that belongs to
   * @return all angularobject in the scope
   */
  public List<AngularObject> getAll(String noteId, String paragraphId) {
    List<AngularObject> all = new LinkedList<AngularObject>();
    synchronized (registry) {
      Map<String, AngularObject> r = getRegistryForKey(noteId, paragraphId);
      if (r != null) {
        all.addAll(r.values());
      }
    }
    return all;
  }
  
  /**
   * Get all angular object related to specific note.
   * That includes all global scope objects, notebook scope objects and paragraph scope objects
   * belongs to the noteId.
   *
   * @param noteId
   * @return
   */
  public List<AngularObject> getAllWithGlobal(String noteId) {
    List<AngularObject> all = new LinkedList<AngularObject>();
    synchronized (registry) {
      Map<String, AngularObject> global = getRegistryForKey(null, null);
      if (global != null) {
        all.addAll(global.values());
      }
      for (String key : registry.keySet()) {
        if (key.startsWith(noteId)) {
          all.addAll(registry.get(key).values());
        }
      }
    }
    return all;
  }

  public String getInterpreterGroupId() {
    return interpreterId;
  }

  public Map<String, Map<String, AngularObject>> getRegistry() {
    return registry;
  }

  public void setRegistry(Map<String, Map<String, AngularObject>> registry) {
    this.registry = registry;
  }
}
