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
 * The InterpreterinterpreterHookRegistry specifies code to be conditionally executed by an
 * interpreter. The constants defined in this class denote currently
 * supported events. Each instance is bound to a single InterpreterGroup.
 * Scope is determined on a per-note basis (except when null for global scope).
 */
public class InterpreterHookRegistry {
  public static final String GLOBAL_KEY = "_GLOBAL_";
  private String interpreterId;
  private Map<String, Map<String, Map<String, String>>> registry = new HashMap<>();

  /**
   * hookRegistry constructor.
   *
   * @param interpreterId The Id of the InterpreterGroup instance to bind to
   */
  public InterpreterHookRegistry(final String interpreterId) {
    this.interpreterId = interpreterId;
  }
  
  /**
   * Get the interpreterGroup id this instance is bound to
   */
  public String getInterpreterId() {
    return interpreterId;
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
   * Adds a className to the registry
   *
   * @param noteId The note id
   * @param className The name of the interpreter repl to map the hooks to
   */
  public void addRepl(String noteId, String className) {
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
                       String event, String cmd) throws IllegalArgumentException {
    synchronized (registry) {
      if (noteId == null) {
        noteId = GLOBAL_KEY;
      }
      addRepl(noteId, className);
      if (!event.equals(HookType.POST_EXEC) && !event.equals(HookType.PRE_EXEC) &&
          !event.equals(HookType.POST_EXEC_DEV) && !event.equals(HookType.PRE_EXEC_DEV)) {
        throw new IllegalArgumentException("Must be " + HookType.POST_EXEC + ", " +
                                                        HookType.POST_EXEC_DEV + ", " +
                                                        HookType.PRE_EXEC + " or " +
                                                        HookType.PRE_EXEC_DEV);
      }
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
  public static final class HookType {
    // Execute the hook code PRIOR to main paragraph code execution
    public static final String PRE_EXEC = "pre_exec";
    
    // Execute the hook code AFTER main paragraph code execution
    public static final String POST_EXEC = "post_exec";
    
    // Same as above but reserved for interpreter developers, in order to allow
    // notebook users to use the above without overwriting registry settings
    // that are initialized directly in subclasses of Interpreter.
    public static final String PRE_EXEC_DEV = "pre_exec_dev";
    public static final String POST_EXEC_DEV = "post_exec_dev";
  }
   
}
