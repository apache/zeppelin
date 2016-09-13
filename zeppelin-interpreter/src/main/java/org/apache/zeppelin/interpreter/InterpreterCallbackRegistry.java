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

package org.apache.zeppelin.interpreter;

import java.util.HashMap;
import java.util.Map;

/**
 * The InterpreterinterpreterCallbackRegistry specifies code to be conditionally executed by an
 * interpreter. The constants defined in this class denote currently
 * supported events. Each instance is bound to a single InterpreterGroup
 * and has Note-wide scope.
 */
public class InterpreterCallbackRegistry {
  // Execute the callback code PRIOR to main paragraph code execution
  public static final String PRE_EXEC = "pre_exec";
  
  // Execute the callback code AFTER main paragraph code execution
  public static final String POST_EXEC = "post_exec";
  
  private String interpreterId;
  private Map<String, Map<String, Map<String, String>>> registry =
    new HashMap<String, Map<String, Map<String, String>>>();

  /**
   * CallbackRegistry constructor.
   *
   * @param interpreterId The Id of the InterpreterGroup instance to bind to
   */
  public InterpreterCallbackRegistry(final String interpreterId) {
    this.interpreterId = interpreterId;
  }
  
  /**
   * Adds a note to the registry
   *
   * @param noteId The Id of the Note instance to add
   */
  public void addNote(String noteId) {
    synchronized (registry) {
      if (registry.get(noteId) == null) {
        registry.put(noteId, new HashMap<String, Map<String, String>>());
      }
    }
  }
  
  /**
   * Adds a replName to the registry
   *
   * @param noteId The note id
   * @param replName The name of the interpreter repl to map the callbacks to
   */
  public void addRepl(String noteId, String replName) {
    synchronized (registry) {
      addNote(noteId);
      if (registry.get(noteId).get(replName) == null) {
        registry.get(noteId).put(replName, new HashMap<String, String>());
      }
    }
  }
  
  /**
   * Register a callback for a specific event.
   *
   * @param noteId Denotes the note this instance belongs to
   * @param replName The name of the interpreter repl to map the callbacks to
   * @param event Callback event (see constants defined in this class)
   * @param cmd Code to be executed by the interpreter
   */
  public void register(String noteId, String replName, String event, String cmd) {
    synchronized (registry) {
      addRepl(noteId, replName);
      registry.get(noteId).get(replName).put(event, cmd);
    }
  }
  
  /**
   * Unregister a callback for a specific event.
   *
   * @param noteId Denotes the note this instance belongs to
   * @param replName The name of the interpreter repl to map the callbacks to
   * @param event Callback event (see constants defined in this class)
   */
  public void unregister(String noteId, String replName, String event) {
    synchronized (registry) {
      addRepl(noteId, replName);
      registry.get(noteId).get(replName).remove(event);
    }
  }
  
  /**
   * Get a callback for a specific event.
   *
   * @param noteId Denotes the note this instance belongs to
   * @param replName The name of the interpreter repl to map the callbacks to
   * @param event Callback event (see constants defined in this class)
   */
  public String get(String noteId, String replName, String event) {
    synchronized (registry) {
      addRepl(noteId, replName);
      return registry.get(noteId).get(replName).get(event);
    }
  }
   
}
