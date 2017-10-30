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

import org.apache.zeppelin.annotation.Experimental;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectWatcher;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.ui.OptionInput.ParamOption;
import org.apache.zeppelin.interpreter.remote.RemoteEventClientWrapper;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.ResourceSet;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Base class for ZeppelinContext
 */
public abstract class BaseZeppelinContext {


  protected InterpreterContext interpreterContext;
  protected int maxResult;
  protected InterpreterHookRegistry hooks;
  protected GUI gui;

  private static RemoteEventClientWrapper eventClient;

  public BaseZeppelinContext(InterpreterHookRegistry hooks, int maxResult) {
    this.hooks = hooks;
    this.maxResult = maxResult;
  }

  // Map interpreter class name (to be used by hook registry) from
  // given replName in parapgraph
  public abstract Map<String, String> getInterpreterClassMap();

  public abstract List<Class> getSupportedClasses();

  public int getMaxResult() {
    return this.maxResult;
  }

  /**
   * subclasses should implement this method to display specific data type
   * @param obj
   * @return
   */
  protected abstract String showData(Object obj);

  /**
   * @deprecated use z.textbox instead
   *
   */
  @Deprecated
  @ZeppelinApi
  public Object input(String name) {
    return textbox(name);
  }

  /**
   * @deprecated use z.textbox instead
   */
  @Deprecated
  @ZeppelinApi
  public Object input(String name, Object defaultValue) {
    return textbox(name, defaultValue.toString());
  }

  @ZeppelinApi
  public Object textbox(String name) {
    return textbox(name, "");
  }

  @ZeppelinApi
  public Object textbox(String name, String defaultValue) {
    return gui.textbox(name, defaultValue);
  }

  public Object select(String name, Object defaultValue, ParamOption[] paramOptions) {
    return gui.select(name, defaultValue, paramOptions);
  }

  @ZeppelinApi
  public Collection<Object> checkbox(String name, ParamOption[] options) {
    List<Object> defaultValues = new LinkedList<>();
    for (ParamOption option : options) {
      defaultValues.add(option.getValue());
    }
    return checkbox(name, defaultValues, options);
  }

  @ZeppelinApi
  public Collection<Object> checkbox(String name,
                                     List<Object> defaultValues,
                                     ParamOption[] options) {
    return gui.checkbox(name, defaultValues, options);
  }

  public void setGui(GUI o) {
    this.gui = o;
  }

  public GUI getGui() {
    return gui;
  }

  private void restartInterpreter() {
  }

  public InterpreterContext getInterpreterContext() {
    return interpreterContext;
  }

  public void setInterpreterContext(InterpreterContext interpreterContext) {
    this.interpreterContext = interpreterContext;
  }

  public void setMaxResult(int maxResult) {
    this.maxResult = maxResult;
  }

  /**
   * display special types of objects for interpreter.
   * Each interpreter can has its own supported classes.
   * @param o object
   */
  @ZeppelinApi
  public void show(Object o) {
    show(o, maxResult);
  }

  /**
   * display special types of objects for interpreter.
   * Each interpreter can has its own supported classes.
   * @param o object
   * @param maxResult maximum number of rows to display
   */

  @ZeppelinApi
  public void show(Object o, int maxResult) {
    try {
      if (isSupportedObject(o)) {
        interpreterContext.out.write(showData(o));
      } else {
        interpreterContext.out.write(o.toString());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isSupportedObject(Object obj) {
    for (Class supportedClass : getSupportedClasses()) {
      if (supportedClass.isInstance(obj)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Run paragraph by id
   * @param noteId
   * @param paragraphId
   */
  @ZeppelinApi
  public void run(String noteId, String paragraphId) {
    run(noteId, paragraphId, interpreterContext, true);
  }

  /**
   * Run paragraph by id
   * @param paragraphId
   */
  @ZeppelinApi
  public void run(String paragraphId) {
    run(paragraphId, true);
  }

  /**
   * Run paragraph by id
   * @param paragraphId
   * @param checkCurrentParagraph
   */
  @ZeppelinApi
  public void run(String paragraphId, boolean checkCurrentParagraph) {
    String noteId = interpreterContext.getNoteId();
    run(noteId, paragraphId, interpreterContext, checkCurrentParagraph);
  }

  /**
   * Run paragraph by id
   * @param noteId
   */
  @ZeppelinApi
  public void run(String noteId, String paragraphId, InterpreterContext context) {
    run(noteId, paragraphId, context, true);
  }

  /**
   * Run paragraph by id
   * @param noteId
   * @param context
   */
  @ZeppelinApi
  public void run(String noteId, String paragraphId, InterpreterContext context,
                  boolean checkCurrentParagraph) {
    if (paragraphId.equals(context.getParagraphId()) && checkCurrentParagraph) {
      throw new RuntimeException("Can not run current Paragraph");
    }

    List<InterpreterContextRunner> runners =
        getInterpreterContextRunner(noteId, paragraphId, context);

    if (runners.size() <= 0) {
      throw new RuntimeException("Paragraph " + paragraphId + " not found " + runners.size());
    }

    for (InterpreterContextRunner r : runners) {
      r.run();
    }

  }

  public void runNote(String noteId) {
    runNote(noteId, interpreterContext);
  }

  public void runNote(String noteId, InterpreterContext context) {
    String runningNoteId = context.getNoteId();
    String runningParagraphId = context.getParagraphId();
    List<InterpreterContextRunner> runners = getInterpreterContextRunner(noteId, context);

    if (runners.size() <= 0) {
      throw new RuntimeException("Note " + noteId + " not found " + runners.size());
    }

    for (InterpreterContextRunner r : runners) {
      if (r.getNoteId().equals(runningNoteId) && r.getParagraphId().equals(runningParagraphId)) {
        continue;
      }
      r.run();
    }
  }


  /**
   * get Zeppelin Paragraph Runner from zeppelin server
   * @param noteId
   */
  @ZeppelinApi
  public List<InterpreterContextRunner> getInterpreterContextRunner(
      String noteId, InterpreterContext interpreterContext) {
    List<InterpreterContextRunner> runners = new LinkedList<>();
    RemoteWorksController remoteWorksController = interpreterContext.getRemoteWorksController();

    if (remoteWorksController != null) {
      runners = remoteWorksController.getRemoteContextRunner(noteId);
    }

    return runners;
  }

  /**
   * get Zeppelin Paragraph Runner from zeppelin server
   * @param noteId
   * @param paragraphId
   */
  @ZeppelinApi
  public List<InterpreterContextRunner> getInterpreterContextRunner(
      String noteId, String paragraphId, InterpreterContext interpreterContext) {
    List<InterpreterContextRunner> runners = new LinkedList<>();
    RemoteWorksController remoteWorksController = interpreterContext.getRemoteWorksController();

    if (remoteWorksController != null) {
      runners = remoteWorksController.getRemoteContextRunner(noteId, paragraphId);
    }

    return runners;
  }

  /**
   * Run paragraph at idx
   * @param idx
   */
  @ZeppelinApi
  public void run(int idx) {
    run(idx, true);
  }

  /**
   *
   * @param idx  paragraph index
   * @param checkCurrentParagraph  check whether you call this run method in the current paragraph.
   * Set it to false only when you are sure you are not invoking this method to run current
   * paragraph. Otherwise you would run current paragraph in infinite loop.
   */
  public void run(int idx, boolean checkCurrentParagraph) {
    String noteId = interpreterContext.getNoteId();
    run(noteId, idx, interpreterContext, checkCurrentParagraph);
  }

  /**
   * Run paragraph at index
   * @param noteId
   * @param idx index starting from 0
   * @param context interpreter context
   */
  public void run(String noteId, int idx, InterpreterContext context) {
    run(noteId, idx, context, true);
  }

  /**
   *
   * @param noteId
   * @param idx  paragraph index
   * @param context interpreter context
   * @param checkCurrentParagraph check whether you call this run method in the current paragraph.
   * Set it to false only when you are sure you are not invoking this method to run current
   * paragraph. Otherwise you would run current paragraph in infinite loop.
   */
  public void run(String noteId, int idx, InterpreterContext context,
                  boolean checkCurrentParagraph) {
    List<InterpreterContextRunner> runners = getInterpreterContextRunner(noteId, context);
    if (idx >= runners.size()) {
      throw new RuntimeException("Index out of bound");
    }

    InterpreterContextRunner runner = runners.get(idx);
    if (runner.getParagraphId().equals(context.getParagraphId()) && checkCurrentParagraph) {
      throw new RuntimeException("Can not run current Paragraph: " + runner.getParagraphId());
    }

    runner.run();
  }

  @ZeppelinApi
  public void run(List<Object> paragraphIdOrIdx) {
    run(paragraphIdOrIdx, interpreterContext);
  }

  /**
   * Run paragraphs
   * @param paragraphIdOrIdx list of paragraph id or idx
   */
  @ZeppelinApi
  public void run(List<Object> paragraphIdOrIdx, InterpreterContext context) {
    String noteId = context.getNoteId();
    for (Object idOrIdx : paragraphIdOrIdx) {
      if (idOrIdx instanceof String) {
        String paragraphId = (String) idOrIdx;
        run(noteId, paragraphId, context);
      } else if (idOrIdx instanceof Integer) {
        Integer idx = (Integer) idOrIdx;
        run(noteId, idx, context);
      } else {
        throw new RuntimeException("Paragraph " + idOrIdx + " not found");
      }
    }
  }

  @ZeppelinApi
  public void runAll() {
    runAll(interpreterContext);
  }

  /**
   * Run all paragraphs. except this.
   */
  @ZeppelinApi
  public void runAll(InterpreterContext context) {
    runNote(context.getNoteId());
  }

  @ZeppelinApi
  public List<String> listParagraphs() {
    List<String> paragraphs = new LinkedList<>();

    for (InterpreterContextRunner r : interpreterContext.getRunners()) {
      paragraphs.add(r.getParagraphId());
    }

    return paragraphs;
  }


  private AngularObject getAngularObject(String name, InterpreterContext interpreterContext) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    String noteId = interpreterContext.getNoteId();
    // try get local object
    AngularObject paragraphAo = registry.get(name, noteId, interpreterContext.getParagraphId());
    AngularObject noteAo = registry.get(name, noteId, null);

    AngularObject ao = paragraphAo != null ? paragraphAo : noteAo;

    if (ao == null) {
      // then global object
      ao = registry.get(name, null, null);
    }
    return ao;
  }


  /**
   * Get angular object. Look up notebook scope first and then global scope
   * @param name variable name
   * @return value
   */
  @ZeppelinApi
  public Object angular(String name) {
    AngularObject ao = getAngularObject(name, interpreterContext);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  /**
   * Get angular object. Look up global scope
   * @param name variable name
   * @return value
   */
  @Deprecated
  public Object angularGlobal(String name) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    AngularObject ao = registry.get(name, null, null);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  /**
   * Create angular variable in notebook scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  @ZeppelinApi
  public void angularBind(String name, Object o) {
    angularBind(name, o, interpreterContext.getNoteId());
  }

  /**
   * Create angular variable in global scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  @Deprecated
  public void angularBindGlobal(String name, Object o) {
    angularBind(name, o, (String) null);
  }

  /**
   * Create angular variable in local scope and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  @ZeppelinApi
  public void angularBind(String name, Object o, AngularObjectWatcher watcher) {
    angularBind(name, o, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Create angular variable in global scope and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  @Deprecated
  public void angularBindGlobal(String name, Object o, AngularObjectWatcher watcher) {
    angularBind(name, o, null, watcher);
  }

  /**
   * Add watcher into angular variable (local scope)
   * @param name name of the variable
   * @param watcher watcher
   */
  @ZeppelinApi
  public void angularWatch(String name, AngularObjectWatcher watcher) {
    angularWatch(name, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Add watcher into angular variable (global scope)
   * @param name name of the variable
   * @param watcher watcher
   */
  @Deprecated
  public void angularWatchGlobal(String name, AngularObjectWatcher watcher) {
    angularWatch(name, null, watcher);
  }



  /**
   * Remove watcher from angular variable (local)
   * @param name
   * @param watcher
   */
  @ZeppelinApi
  public void angularUnwatch(String name, AngularObjectWatcher watcher) {
    angularUnwatch(name, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Remove watcher from angular variable (global)
   * @param name
   * @param watcher
   */
  @Deprecated
  public void angularUnwatchGlobal(String name, AngularObjectWatcher watcher) {
    angularUnwatch(name, null, watcher);
  }


  /**
   * Remove all watchers for the angular variable (local)
   * @param name
   */
  @ZeppelinApi
  public void angularUnwatch(String name) {
    angularUnwatch(name, interpreterContext.getNoteId());
  }

  /**
   * Remove all watchers for the angular variable (global)
   * @param name
   */
  @Deprecated
  public void angularUnwatchGlobal(String name) {
    angularUnwatch(name, (String) null);
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  @ZeppelinApi
  public void angularUnbind(String name) {
    String noteId = interpreterContext.getNoteId();
    angularUnbind(name, noteId);
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  @Deprecated
  public void angularUnbindGlobal(String name) {
    angularUnbind(name, null);
  }

  /**
   * Create angular variable in notebook scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   * @param name name of the variable
   * @param o value
   */
  private void angularBind(String name, Object o, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId, null) == null) {
      registry.add(name, o, noteId, null);
    } else {
      registry.get(name, noteId, null).set(o);
    }
  }

  /**
   * Create angular variable in notebook scope and bind with front end Angular display
   * system.
   * If variable exists, value will be overwritten and watcher will be added.
   * @param name name of variable
   * @param o value
   * @param watcher watcher of the variable
   */
  private void angularBind(String name, Object o, String noteId, AngularObjectWatcher watcher) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId, null) == null) {
      registry.add(name, o, noteId, null);
    } else {
      registry.get(name, noteId, null).set(o);
    }
    angularWatch(name, watcher);
  }

  /**
   * Add watcher into angular binding variable
   * @param name name of the variable
   * @param watcher watcher
   */
  public void angularWatch(String name, String noteId, AngularObjectWatcher watcher) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId, null) != null) {
      registry.get(name, noteId, null).addWatcher(watcher);
    }
  }

  /**
   * Remove watcher
   * @param name
   * @param watcher
   */
  private void angularUnwatch(String name, String noteId, AngularObjectWatcher watcher) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    if (registry.get(name, noteId, null) != null) {
      registry.get(name, noteId, null).removeWatcher(watcher);
    }
  }

  /**
   * Remove all watchers for the angular variable
   * @param name
   */
  private void angularUnwatch(String name, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    if (registry.get(name, noteId, null) != null) {
      registry.get(name, noteId, null).clearAllWatchers();
    }
  }

  /**
   * Remove angular variable and all the watchers.
   * @param name
   */
  private void angularUnbind(String name, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    registry.remove(name, noteId, null);
  }

  /**
   * Get the interpreter class name from name entered in paragraph
   * @param replName if replName is a valid className, return that instead.
   */
  public String getClassNameFromReplName(String replName) {
    for (String name : getInterpreterClassMap().keySet()) {
      if (replName.equals(name)) {
        return replName;
      }
    }

    if (replName.contains("spark.")) {
      replName = replName.replace("spark.", "");
    }
    return getInterpreterClassMap().get(replName);
  }

  /**
   * General function to register hook event
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param cmd The code to be executed by the interpreter on given event
   * @param replName Name of the interpreter
   */
  @Experimental
  public void registerHook(String event, String cmd, String replName) {
    String noteId = interpreterContext.getNoteId();
    String className = getClassNameFromReplName(replName);
    hooks.register(noteId, className, event, cmd);
  }

  /**
   * registerHook() wrapper for current repl
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param cmd The code to be executed by the interpreter on given event
   */
  @Experimental
  public void registerHook(String event, String cmd) {
    String className = interpreterContext.getClassName();
    registerHook(event, cmd, className);
  }

  /**
   * Get the hook code
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param replName Name of the interpreter
   */
  @Experimental
  public String getHook(String event, String replName) {
    String noteId = interpreterContext.getNoteId();
    String className = getClassNameFromReplName(replName);
    return hooks.get(noteId, className, event);
  }

  /**
   * getHook() wrapper for current repl
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public String getHook(String event) {
    String className = interpreterContext.getClassName();
    return getHook(event, className);
  }

  /**
   * Unbind code from given hook event
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param replName Name of the interpreter
   */
  @Experimental
  public void unregisterHook(String event, String replName) {
    String noteId = interpreterContext.getNoteId();
    String className = getClassNameFromReplName(replName);
    hooks.unregister(noteId, className, event);
  }

  /**
   * unregisterHook() wrapper for current repl
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public void unregisterHook(String event) {
    String className = interpreterContext.getClassName();
    unregisterHook(event, className);
  }

  /**
   * Add object into resource pool
   * @param name
   * @param value
   */
  @ZeppelinApi
  public void put(String name, Object value) {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    resourcePool.put(name, value);
  }

  /**
   * Get object from resource pool
   * Search local process first and then the other processes
   * @param name
   * @return null if resource not found
   */
  @ZeppelinApi
  public Object get(String name) {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    Resource resource = resourcePool.get(name);
    if (resource != null) {
      return resource.get();
    } else {
      return null;
    }
  }

  /**
   * Remove object from resourcePool
   * @param name
   */
  @ZeppelinApi
  public void remove(String name) {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    resourcePool.remove(name);
  }

  /**
   * Check if resource pool has the object
   * @param name
   * @return
   */
  @ZeppelinApi
  public boolean containsKey(String name) {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    Resource resource = resourcePool.get(name);
    return resource != null;
  }

  /**
   * Get all resources
   */
  @ZeppelinApi
  public ResourceSet getAll() {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    return resourcePool.getAll();
  }

  /**
   * Get the event client
   */
  @ZeppelinApi
  public static RemoteEventClientWrapper getEventClient() {
    return eventClient;
  }

  /**
   * Set event client
   */
  @ZeppelinApi
  public void setEventClient(RemoteEventClientWrapper eventClient) {
    if (BaseZeppelinContext.eventClient == null) {
      BaseZeppelinContext.eventClient = eventClient;
    }
  }
}
