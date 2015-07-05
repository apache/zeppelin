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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.zeppelin.scheduler.ExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 * @param <T>
 */
public class AngularObject<T> {
  private String name;
  private T object;
  
  private transient AngularObjectListener listener;
  private transient List<AngularObjectWatcher> watchers
    = new LinkedList<AngularObjectWatcher>();
  
  private String noteId;   // noteId belonging to. null for global scope 

  protected AngularObject(String name, T o, String noteId,
      AngularObjectListener listener) {
    this.name = name;
    this.noteId = noteId;
    this.listener = listener;
    object = o;
  }

  public String getName() {
    return name;
  }
  
  public void setNoteId(String noteId) {
    this.noteId = noteId;
  }
  
  public String getNoteId() {
    return noteId;
  }
  
  public boolean isGlobal() {
    return noteId == null;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof AngularObject) {
      AngularObject ao = (AngularObject) o;
      if (noteId == null && ao.noteId == null ||
          (noteId != null && ao.noteId != null && noteId.equals(ao.noteId))) {
        return name.equals(ao.name);
      }
    }
    return false;
  }

  public Object get() {
    return object;
  }

  public void emit(){
    if (listener != null) {
      listener.updated(this);
    }
  }
  
  public void set(T o) {
    set(o, true);
  }

  public void set(T o, boolean emit) {
    final T before = object;
    final T after = o;
    object = o;
    if (emit) {
      emit();
    }

    final Logger logger = LoggerFactory.getLogger(AngularObject.class);
    List<AngularObjectWatcher> ws = new LinkedList<AngularObjectWatcher>();
    synchronized (watchers) {
      ws.addAll(watchers);
    }

    ExecutorService executor = ExecutorFactory.singleton().createOrGet("angularObjectWatcher", 50);
    for (final AngularObjectWatcher w : ws) {
      executor.submit(new Runnable() {
        @Override
        public void run() {
          try {
            w.watch(before, after);
          } catch (Exception e) {
            logger.error("Exception on watch", e);
          }
        }
      });
    }
  }

  public void setListener(AngularObjectListener listener) {
    this.listener = listener;
  }

  public AngularObjectListener getListener() {
    return listener;
  }

  public void addWatcher(AngularObjectWatcher watcher) {
    synchronized (watchers) {
      watchers.add(watcher);
    }
  }

  public void removeWatcher(AngularObjectWatcher watcher) {
    synchronized (watchers) {
      watchers.remove(watcher);
    }
  }

  public void clearAllWatchers() {
    synchronized (watchers) {
      watchers.clear();
    }
  }

}
