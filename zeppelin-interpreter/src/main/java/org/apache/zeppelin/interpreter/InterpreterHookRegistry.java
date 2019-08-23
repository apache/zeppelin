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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The InterpreterHookRegistry specifies code to be conditionally executed by an
 * interpreter. The constants defined in this class denote currently
 * supported events. Each instance is bound to a single InterpreterGroup.
 * Scope is determined on a per-note basis (except when null for global scope).
 */
public class InterpreterHookRegistry {
  static final String GLOBAL_KEY = "_GLOBAL_";

  // Scope (noteId/global scope) -> (ClassName -> (EventType -> Hook Code))
  private Map<String, Map<String, Map<String, String>>> registry = new HashMap<>();


  /**
   * Adds a note to the registry
   *
   * @param noteId The Id of the Note instance to add
   */
  private void addNote(String noteId) {
    synchronized (registry) {
      if (registry.get(noteId) == null) {
        registry.put(noteId, new HashMap<String, Map<String, String>>());
      }
    }
  }
  
  /**
   * Adds a className to the registry
   *
   * @param noteId The note id
   * @param className The name of the interpreter repl to map the hooks to
   */
  private void addRepl(String noteId, String className) {
    synchronized (registry) {
      addNote(noteId);
      if (registry.get(noteId).get(className) == null) {
        registry.get(noteId).put(className, new HashMap<String, String>());
      }
    }
  }
  
  /**
   * Register a hook for a specific event.
   *
   * @param noteId Denotes the note this instance belongs to
   * @param className The name of the interpreter repl to map the hooks to
   * @param event hook event (see constants defined in this class)
   * @param cmd Code to be executed by the interpreter
   */
  public void register(String noteId, String className,
                       String event, String cmd) throws InvalidHookException {
    synchronized (registry) {
      if (!HookType.ValidEvents.contains(event)) {
        throw new InvalidHookException("event " + event + " is not valid hook event");
      }
      if (noteId == null) {
        noteId = GLOBAL_KEY;
      }
      addRepl(noteId, className);
      registry.get(noteId).get(className).put(event, cmd);
    }
  }
  
  /**
   * Unregister a hook for a specific event.
   *
   * @param noteId Denotes the note this instance belongs to
   * @param className The name of the interpreter repl to map the hooks to
   * @param event hook event (see constants defined in this class)
   */
  public void unregister(String noteId, String className, String event) {
    synchronized (registry) {
      if (noteId == null) {
        noteId = GLOBAL_KEY;
      }
      addRepl(noteId, className);
      registry.get(noteId).get(className).remove(event);
    }
  }
  
  /**
   * Get a hook for a specific event.
   *
   * @param noteId Denotes the note this instance belongs to
   * @param className The name of the interpreter repl to map the hooks to
   * @param event hook event (see constants defined in this class)
   */
  public String get(String noteId, String className, String event) {
    synchronized (registry) {
      if (noteId == null) {
        noteId = GLOBAL_KEY;
      }
      addRepl(noteId, className);
      return registry.get(noteId).get(className).get(event);
    }
  }
  
  /**
  * Container for hook event type constants
  */
  public enum HookType {

    // Execute the hook code PRIOR to main paragraph code execution
    PRE_EXEC("pre_exec"),
    
    // Execute the hook code AFTER main paragraph code execution
    POST_EXEC("post_exec"),
    
    // Same as above but reserved for interpreter developers, in order to allow
    // notebook users to use the above without overwriting registry settings
    // that are initialized directly in subclasses of Interpreter.
    PRE_EXEC_DEV("pre_exec_dev"),
    POST_EXEC_DEV("post_exec_dev");

    private String name;

    HookType(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public static Set<String> ValidEvents = new HashSet();
    static {
      for (HookType type : values()) {
        ValidEvents.add(type.getName());
      }
    }
  }
   
}
