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
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import org.apache.zeppelin.scheduler.ExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AngularObject provides binding between back-end (interpreter) and front-end
 * User provided object will automatically synchronized with front-end side.
 * i.e. update from back-end will be sent to front-end, update from front-end will sent-to backend
 *
 * @param <T>
 */
public class AngularObject<T> {
  private String name;
  private T object;
  
  private transient AngularObjectListener listener;
  private transient List<AngularObjectWatcher> watchers = new LinkedList<>();
  
  private String noteId;   // noteId belonging to. null for global scope 
  private String paragraphId; // paragraphId belongs to. null for notebook scope

  /**
   * Public constructor, neccessary for the deserialization when using Thrift angularRegistryPush()
   * Without public constructor, GSON library will instantiate the AngularObject using
   * serialization so the <strong>watchers</strong> list won't be initialized and will throw
   * NullPointerException the first time it is accessed
   */
  public AngularObject() {
  }

  /**
   * To create new AngularObject, use AngularObjectRegistry.add()
   *
   * @param name name of object
   * @param o reference to user provided object to sent to front-end
   * @param noteId noteId belongs to. can be null
   * @param paragraphId paragraphId belongs to. can be null
   * @param listener event listener
   */
  protected AngularObject(String name, T o, String noteId, String paragraphId,
      AngularObjectListener listener) {
    this.name = name;
    this.noteId = noteId;
    this.paragraphId = paragraphId;
    this.listener = listener;
    object = o;
  }

  /**
   * Get name of this object
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Set noteId
   * @param noteId noteId belongs to. can be null
   */
  public void setNoteId(String noteId) {
    this.noteId = noteId;
  }

  /**
   * Get noteId
   * @return noteId
   */
  public String getNoteId() {
    return noteId;
  }

  /**
   * get ParagraphId
   * @return paragraphId
   */
  public String getParagraphId() {
    return paragraphId;
  }

  /**
   * Set paragraphId
   * @param paragraphId paragraphId. can be null
   */
  public void setParagraphId(String paragraphId) {
    this.paragraphId = paragraphId;
  }

  /**
   * Check if it is global scope object
   * @return true it is global scope
   */
  public boolean isGlobal() {
    return noteId == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AngularObject<?> that = (AngularObject<?>) o;
    return Objects.equals(name, that.name) &&
            Objects.equals(noteId, that.noteId) &&
            Objects.equals(paragraphId, that.paragraphId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, noteId, paragraphId);
  }

  /**
   * Get value
   * @return
   */
  public Object get() {
    return object;
  }

  /**
   * fire updated() event for listener
   * Note that it does not invoke watcher.watch()
   */
  public void emit(){
    if (listener != null) {
      listener.updated(this);
    }
  }

  /**
   * Set value
   * @param o reference to new user provided object
   */
  public void set(T o) {
    set(o, true);
  }

  /**
   * Set value
   * @param o reference to new user provided object
   * @param emit false on skip firing event for listener. note that it does not skip invoke
   *             watcher.watch() in any case
   */
  public void set(T o, boolean emit) {
    final T before = object;
    final T after = o;
    object = o;
    if (emit) {
      emit();
    }

    final Logger logger = LoggerFactory.getLogger(AngularObject.class);
    List<AngularObjectWatcher> ws = new LinkedList<>();
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

  /**
   * Set event listener for this object
   * @param listener
   */
  public void setListener(AngularObjectListener listener) {
    this.listener = listener;
  }

  /**
   * Get event listener of this object
   * @return event listener
   */
  public AngularObjectListener getListener() {
    return listener;
  }

  /**
   * Add a watcher for this object.
   * Multiple watcher can be registered.
   *
   * @param watcher watcher to add
   */
  public void addWatcher(AngularObjectWatcher watcher) {
    synchronized (watchers) {
      watchers.add(watcher);
    }
  }

  /**
   * Remove a watcher from this object
   * @param watcher watcher to remove
   */
  public void removeWatcher(AngularObjectWatcher watcher) {
    synchronized (watchers) {
      watchers.remove(watcher);
    }
  }

  /**
   * Remove all watchers from this object
   */
  public void clearAllWatchers() {
    synchronized (watchers) {
      watchers.clear();
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("AngularObject{");
    sb.append("noteId='").append(noteId).append('\'');
    sb.append(", paragraphId='").append(paragraphId).append('\'');
    sb.append(", object=").append(object);
    sb.append(", name='").append(name).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
