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
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.ResourceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abstract class for ZeppelinContext
 */
public abstract class ZeppelinContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinContext.class);

  protected InterpreterContext interpreterContext;
  protected int maxResult;
  protected InterpreterHookRegistry hooks;
  protected GUI gui;
  protected GUI noteGui;

  public ZeppelinContext(InterpreterHookRegistry hooks, int maxResult) {
    this.hooks = hooks;
    this.maxResult = maxResult;
  }

  // Map interpreter class name (to be used by hook registry) from
  // given replName in paragraph
  public abstract Map<String, String> getInterpreterClassMap();

  public abstract List<Class> getSupportedClasses();

  public int getMaxResult() {
    return this.maxResult;
  }

  public String showData(Object obj) {
    return showData(obj, maxResult);
  }

  /**
   * subclasses should implement this method to display specific data type
   *
   * @param obj
   * @param maxResult  max number of rows to display
   * @return
   */
  public abstract String showData(Object obj, int maxResult);

  /**
   * Create paragraph level dynamic form of textbox with empty value.
   * @deprecated Use {@link #textbox(String) textbox} instead.
   */
  @Deprecated
  @ZeppelinApi
  public Object input(String name) {
    return textbox(name);
  }

  /**
   * Create paragraph level dynamic form of textbox with empty value.
   * @deprecated Use {@link #textbox(String, String) textbox} instead.
   */
  @Deprecated
  @ZeppelinApi
  public Object input(String name, Object defaultValue) {
    return textbox(name, defaultValue.toString(), false);
  }

  /**
   * Create paragraph level dynamic form of textbox with empty value.
   * TODO(zjffdu) Return String instead
   *
   * @param name
   * @return text value of this textbox
   */
  @ZeppelinApi
  public Object textbox(String name) {
    return textbox(name, "");
  }

  /**
   * Create paragraph level dynamic form of textbox with default value.
   *
   * @param name
   * @param defaultValue
   * @return text value of this textbox
   */
  @ZeppelinApi
  public Object textbox(String name, String defaultValue) {
    return textbox(name, defaultValue, false);
  }

  /**
   * Create note level dynamic form of textbox with empty value.
   *
   * @param name
   * @return text value of this textbox
   */
  @ZeppelinApi
  public Object noteTextbox(String name) {
    return noteTextbox(name, "");
  }

  /**
   * Create note level dynamic form of textbox with default value.
   *
   * @param name
   * @param defaultValue
   * @return text value of this textbox
   */
  @ZeppelinApi
  public Object noteTextbox(String name, String defaultValue) {
    return textbox(name, defaultValue, true);
  }

  private Object textbox(String name, String defaultValue, boolean noteForm) {
    if (noteForm) {
      return noteGui.textbox(name, defaultValue);
    } else {
      return gui.textbox(name, defaultValue);
    }
  }

  /**
   * Create paragraph level dynamic form of password.
   *
   * @param name
   * @return  text value of this password
   */
  @ZeppelinApi
  public Object password(String name) {
    return password(name, false);
  }

  /**
   * Create note level dynamic form of password.
   *
   * @param name
   * @return text value of this password
   */
  @ZeppelinApi
  public Object notePassword(String name) {
    return password(name, true);
  }

  private Object password(String name, boolean noteForm) {
    if (noteForm) {
      return noteGui.password(name);
    } else {
      return gui.password(name);
    }
  }

  /**
   * create paragraph level of dynamic form of checkbox with no item checked.
   *
   * @param name
   * @param options
   * @return list of checked values of this checkbox
   */
  @ZeppelinApi
  public List<Object> checkbox(String name, ParamOption[] options) {
    return checkbox(name, options, null, false);
  }

  /**
   * create paragraph level of dynamic form of checkbox with default checked items.
   *
   * @param name
   * @param options
   * @param defaultChecked
   * @return list of checked values of this checkbox
   */
  @ZeppelinApi
  public List<Object> checkbox(String name,
                               ParamOption[] options,
                               List defaultChecked) {
    return checkbox(name, options, defaultChecked, false);
  }

  /**
   * create paragraph level of dynamic form of checkbox with default checked items.
   * @deprecated Use {@link #checkbox(String, ParamOption[], List<Object>) checkbox} instead.
   *
   * @param name
   * @param defaultChecked
   * @param options
   * @return list of checked values of this checkbox
   */
  @Deprecated
  @ZeppelinApi
  public List<Object> checkbox(String name,
                               List<Object> defaultChecked,
                               ParamOption[] options) {
    return checkbox(name, options, defaultChecked, false);
  }

  /**
   * create note level of dynamic form of checkbox with no item checked.
   *
   * @param name
   * @param options
   * @return list of checked values of this checkbox
   */
  @ZeppelinApi
  public List<Object> noteCheckbox(String name, ParamOption[] options) {
    return checkbox(name, options, null, true);
  }

  /**
   * create note level of dynamic form of checkbox with default checked items.
   * @deprecated Use {@link #noteCheckbox(String, ParamOption[], List<Object>) noteCheckbox} instead.
   *
   * @param name
   * @param defaultChecked
   * @param options
   * @return list of checked values of this checkbox
   */
  @Deprecated
  @ZeppelinApi
  public List<Object> noteCheckbox(String name,
                                   List<Object> defaultChecked,
                                   ParamOption[] options) {
    return checkbox(name, options, defaultChecked, true);
  }

  /**
   * create note level of dynamic form of checkbox with default checked items.
   *
   * @param name
   * @param options
   * @param defaultChecked
   * @return list of checked values of this checkbox
   */
  @ZeppelinApi
  public List<Object> noteCheckbox(String name,
                                   ParamOption[] options,
                                   List defaultChecked) {
    return checkbox(name, options, defaultChecked, true);
  }

  private List<Object> checkbox(String name,
                                ParamOption[] options,
                                List<Object> defaultChecked,
                                boolean noteForm) {
    if (defaultChecked == null ) {
      defaultChecked = new ArrayList<>();
      for (ParamOption option : options) {
        defaultChecked.add(option.getValue());
      }
    }
    if (noteForm) {
      return noteGui.checkbox(name, options, defaultChecked);
    } else {
      return gui.checkbox(name, options, defaultChecked);
    }
  }

  /**
   * create paragraph level of dynamic form of Select with no item selected.
   *
   * @param name
   * @param paramOptions
   * @return text value of selected item
   */
  @ZeppelinApi
  public Object select(String name, ParamOption[] paramOptions) {
    return select(name, paramOptions, null, false);
  }

  /**
   * create paragraph level of dynamic form of Select with default selected item.
   * @deprecated Use {@link #select(String, ParamOption[], Object) select} instead.
   *
   * @param name
   * @param defaultValue
   * @param paramOptions
   * @return text value of selected item
   */
  @Deprecated
  @ZeppelinApi
  public Object select(String name, Object defaultValue, ParamOption[] paramOptions) {
    return select(name, paramOptions, defaultValue, false);
  }

  /**
   * create paragraph level of dynamic form of Select with default selected item.
   *
   * @param name
   * @param paramOptions
   * @param defaultValue
   * @return text value of selected item
   */
  @ZeppelinApi
  public Object select(String name, ParamOption[] paramOptions, Object defaultValue) {
    return select(name, paramOptions, defaultValue, false);
  }

  /**
   * create paragraph level of dynamic form of Select with no item selected.
   *
   * @param name
   * @param paramOptions
   * @return text value of selected item
   */
  @ZeppelinApi
  public Object noteSelect(String name, ParamOption[] paramOptions) {
    return select(name, null, paramOptions, true);
  }

  /**
   * create note level of dynamic form of Select with default selected item.
   * @deprecated Use {@link #noteSelect(String, ParamOption[], Object) noteSelect} instead.
   *
   * @param name
   * @param defaultValue
   * @param paramOptions
   * @return text value of selected item
   */
  @Deprecated
  @ZeppelinApi
  public Object noteSelect(String name, Object defaultValue, ParamOption[] paramOptions) {
    return select(name, paramOptions, defaultValue, true);
  }

  /**
   * create note level of dynamic form of Select with default selected item.
   *
   * @param name
   * @param paramOptions
   * @param defaultValue
   * @return text value of selected item
   */
  @ZeppelinApi
  public Object noteSelect(String name, ParamOption[] paramOptions, Object defaultValue) {
    return select(name, paramOptions, defaultValue, true);
  }

  private Object select(String name, ParamOption[] paramOptions, Object defaultValue,
                        boolean noteForm) {
    if (noteForm) {
      return noteGui.select(name, paramOptions, defaultValue);
    } else {
      return gui.select(name, paramOptions, defaultValue);
    }
  }

  public void setGui(GUI o) {
    this.gui = o;
  }

  public GUI getGui() {
    return gui;
  }

  public GUI getNoteGui() {
    return noteGui;
  }

  public void setNoteGui(GUI noteGui) {
    this.noteGui = noteGui;
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
   *
   * @param o object
   */
  @ZeppelinApi
  public void show(Object o) {
    show(o, maxResult);
  }

  /**
   * display special types of objects for interpreter.
   * Each interpreter can has its own supported classes.
   *
   * @param o         object
   * @param maxResult maximum number of rows to display
   */
  @ZeppelinApi
  public void show(Object o, int maxResult) {
    try {
      if (isSupportedObject(o)) {
        interpreterContext.out.write(showData(o));
      } else {
        interpreterContext.out.write("ZeppelinContext doesn't support to show type: "
            + o.getClass().getCanonicalName() + "\n");
        interpreterContext.out.write(o.toString());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected boolean isSupportedObject(Object obj) {
    for (Class supportedClass : getSupportedClasses()) {
      if (supportedClass.isInstance(obj)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Run paragraph by id
   *
   * @param paragraphId
   */
  @ZeppelinApi
  public void run(String paragraphId) throws IOException {
    run(paragraphId, true);
  }

  /**
   * Run paragraph by id
   *
   * @param paragraphId
   * @param checkCurrentParagraph check whether you call this run method in the current paragraph.
   *          Set it to false only when you are sure you are not invoking this method to run current
   *          paragraph. Otherwise you would run current paragraph in infinite loop.
   */
  @ZeppelinApi
  public void run(String paragraphId, boolean checkCurrentParagraph) throws IOException {
    String noteId = interpreterContext.getNoteId();
    run(noteId, paragraphId, interpreterContext, checkCurrentParagraph);
  }

  @ZeppelinApi
  public void run(String noteId, String paragraphId)
      throws IOException {
    run(noteId, paragraphId, InterpreterContext.get(), true);
  }

  /**
   * Run paragraph by id
   *
   * @param noteId
   */
  public void run(String noteId, String paragraphId, InterpreterContext context)
      throws IOException {
    run(noteId, paragraphId, context, true);
  }

  /**
   * Run paragraph by id
   *
   * @param noteId
   * @param context
   */
  private void run(String noteId, String paragraphId, InterpreterContext context,
                  boolean checkCurrentParagraph) throws IOException {

    if (paragraphId.equals(context.getParagraphId()) && checkCurrentParagraph) {
      throw new RuntimeException("Can not run current Paragraph");
    }
    List<String> paragraphIds = new ArrayList<>();
    paragraphIds.add(paragraphId);
    List<Integer> paragraphIndices = new ArrayList<>();
    context.getIntpEventClient()
        .runParagraphs(noteId, paragraphIds, paragraphIndices, context.getParagraphId());
  }

  @ZeppelinApi
  public void runNote(String noteId) throws IOException {
    runNote(noteId, interpreterContext);
  }

  public void runNote(String noteId, InterpreterContext context) throws IOException {
    List<String> paragraphIds = new ArrayList<>();
    List<Integer> paragraphIndices = new ArrayList<>();
    context.getIntpEventClient()
        .runParagraphs(noteId, paragraphIds, paragraphIndices, context.getParagraphId());
  }

  /**
   * Run paragraph at idx
   *
   * @param idx
   */
  @ZeppelinApi
  public void run(int idx) throws IOException {
    run(idx, true);
  }

  /**
   * @param idx                   paragraph index
   * @param checkCurrentParagraph check whether you call this run method in the current paragraph.
   *          Set it to false only when you are sure you are not invoking this method to run current
   *          paragraph. Otherwise you would run current paragraph in infinite loop.
   */
  @ZeppelinApi
  public void run(int idx, boolean checkCurrentParagraph) throws IOException {
    String noteId = interpreterContext.getNoteId();
    run(noteId, idx, interpreterContext, checkCurrentParagraph);
  }

  /**
   * Run paragraph at index
   *
   * @param noteId
   * @param idx     index starting from 0
   * @param context interpreter context
   */
  private void run(String noteId, int idx, InterpreterContext context) throws IOException {
    run(noteId, idx, context, true);
  }

  /**
   * @param noteId
   * @param idx                   paragraph index
   * @param context               interpreter context
   * @param checkCurrentParagraph
   * check whether you call this run method in the current paragraph.
   * Set it to false only when you are sure you are not invoking this method to run current
   * paragraph. Otherwise you would run current paragraph in infinite loop.
   */
  private void run(String noteId, int idx, InterpreterContext context,
                  boolean checkCurrentParagraph) throws IOException {

    List<String> paragraphIds = new ArrayList<>();
    List<Integer> paragraphIndices = new ArrayList<>();
    paragraphIndices.add(idx);
    context.getIntpEventClient()
        .runParagraphs(noteId, paragraphIds, paragraphIndices, context.getParagraphId());
  }

  /**
   * Run all paragraphs of current note except this.
   *
   * @throws IOException
   */
  @ZeppelinApi
  public void runAll() throws IOException {
    runAll(interpreterContext);
  }

  /**
   * Run all paragraphs. except this.
   *
   * @param context
   * @throws IOException
   */
  public void runAll(InterpreterContext context) throws IOException {
    runNote(context.getNoteId());
  }

  private AngularObject getAngularObject(String name,
                                         String noteId,
                                         String paragraphId,
                                         InterpreterContext interpreterContext) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    AngularObject ao = registry.get(name, noteId, paragraphId);
    return ao;
  }


  /**
   * Get angular object. Look up note scope first and then global scope
   *
   * @param name variable name
   * @return value
   */
  @ZeppelinApi
  public Object angular(String name) {
    AngularObject ao = getAngularObject(name, interpreterContext.getNoteId(),
            interpreterContext.getParagraphId(), interpreterContext);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  /**
   * Get note scope angular object.
   *
   * @param name
   * @param noteId
   * @return value
   */
  public Object angular(String name, String noteId) {
    AngularObject ao = getAngularObject(name, noteId,
            interpreterContext.getParagraphId(), interpreterContext);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  /**
   * Get paragraph scope angular object.
   *
   * @param name
   * @param noteId
   * @param paragraphId
   * @return value
   */
  public Object angular(String name, String noteId, String paragraphId) {
    AngularObject ao = getAngularObject(name, noteId, paragraphId, interpreterContext);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  /**
   * Get angular object. Look up global scope
   *
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
   * Create angular variable in note scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   *
   * @param name name of the variable
   * @param o    value
   */
  @ZeppelinApi
  public void angularBind(String name, Object o) {
    angularBind(name, o, interpreterContext.getNoteId());
  }

  /**
   * Create angular variable in global scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   *
   * @param name name of the variable
   * @param o    value
   */
  @Deprecated
  public void angularBindGlobal(String name, Object o) {
    angularBind(name, o, (String) null);
  }

  /**
   * Create angular variable in local scope and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   *
   * @param name    name of variable
   * @param o       value
   * @param watcher watcher of the variable
   */
  @ZeppelinApi
  public void angularBind(String name, Object o, AngularObjectWatcher watcher) {
    angularBind(name, o, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Create angular variable in global scope and bind with front end Angular display system.
   * If variable exists, value will be overwritten and watcher will be added.
   *
   * @param name    name of variable
   * @param o       value
   * @param watcher watcher of the variable
   */
  @Deprecated
  public void angularBindGlobal(String name, Object o, AngularObjectWatcher watcher) {
    angularBind(name, o, null, watcher);
  }

  /**
   * Add watcher into angular variable (local scope)
   *
   * @param name    name of the variable
   * @param watcher watcher
   */
  @ZeppelinApi
  public void angularWatch(String name, AngularObjectWatcher watcher) {
    angularWatch(name, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Add watcher into angular variable (global scope)
   *
   * @param name    name of the variable
   * @param watcher watcher
   */
  @Deprecated
  public void angularWatchGlobal(String name, AngularObjectWatcher watcher) {
    angularWatch(name, null, watcher);
  }


  /**
   * Remove watcher from angular variable (local)
   *
   * @param name
   * @param watcher
   */
  @ZeppelinApi
  public void angularUnwatch(String name, AngularObjectWatcher watcher) {
    angularUnwatch(name, interpreterContext.getNoteId(), watcher);
  }

  /**
   * Remove watcher from angular variable (global)
   *
   * @param name
   * @param watcher
   */
  @Deprecated
  public void angularUnwatchGlobal(String name, AngularObjectWatcher watcher) {
    angularUnwatch(name, null, watcher);
  }


  /**
   * Remove all watchers for the angular variable (local)
   *
   * @param name
   */
  @ZeppelinApi
  public void angularUnwatch(String name) {
    angularUnwatch(name, interpreterContext.getNoteId());
  }

  /**
   * Remove all watchers for the angular variable (global)
   *
   * @param name
   */
  @Deprecated
  public void angularUnwatchGlobal(String name) {
    angularUnwatch(name, (String) null);
  }

  /**
   * Remove angular variable and all the watchers.
   *
   * @param name
   */
  @ZeppelinApi
  public void angularUnbind(String name) {
    String noteId = interpreterContext.getNoteId();
    angularUnbind(name, noteId);
  }

  /**
   * Remove angular variable and all the watchers.
   *
   * @param name
   */
  @Deprecated
  public void angularUnbindGlobal(String name) {
    angularUnbind(name, null);
  }

  /**
   * Create angular variable in note scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   *
   * @param name name of the variable
   * @param o    value
   * @param noteId
   */
  public void angularBind(String name, Object o, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId, null) == null) {
      registry.add(name, o, noteId, null);
    } else {
      registry.get(name, noteId, null).set(o);
    }
  }

  /**
   * Create angular variable in note scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   *
   * @param name name of the variable
   * @param o    value
   * @param noteId
   * @param paragraphId
   */
  public void angularBind(String name, Object o, String noteId, String paragraphId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId, paragraphId) == null) {
      registry.add(name, o, noteId, paragraphId);
    } else {
      registry.get(name, noteId, paragraphId).set(o);
    }
  }

  /**
   * Create angular variable in note scope and bind with front end Angular display
   * system.
   * If variable exists, value will be overwritten and watcher will be added.
   *
   * @param name    name of variable
   * @param o       value
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
   *
   * @param name    name of the variable
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
   *
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
   *
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
   *
   * @param name
   */
  private void angularUnbind(String name, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();
    registry.remove(name, noteId, null);
  }

  /**
   * Get the interpreter class name from name entered in paragraph
   *
   * @param replName if replName is a valid className, return that instead.
   */
  private String getClassNameFromReplName(String replName) {
    String[] splits = replName.split(".");
    if (splits.length > 1) {
      replName = splits[splits.length - 1];
    }
    return getInterpreterClassMap().get(replName);
  }

  /**
   * General function to register hook event
   *
   * @param event    The type of event to hook to (pre_exec, post_exec)
   * @param cmd      The code to be executed by the interpreter on given event
   * @param replName Name of the interpreter
   */
  @Experimental
  public void registerHook(String event, String cmd, String replName) throws InvalidHookException {
    String className = getClassNameFromReplName(replName);
    hooks.register(null, className, event, cmd);
  }

  /**
   * registerHook() wrapper for current repl
   *
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param cmd   The code to be executed by the interpreter on given event
   */
  @Experimental
  public void registerHook(String event, String cmd) throws InvalidHookException {
    String replClassName = interpreterContext.getInterpreterClassName();
    hooks.register(null, replClassName, event, cmd);
  }

  /**
   * @param event
   * @param cmd
   * @param noteId
   * @throws InvalidHookException
   */
  @Experimental
  public void registerNoteHook(String event, String cmd, String noteId)
      throws InvalidHookException {
    String replClassName = interpreterContext.getInterpreterClassName();
    hooks.register(noteId, replClassName, event, cmd);
  }

  @Experimental
  public void registerNoteHook(String event, String cmd, String noteId, String replName)
      throws InvalidHookException {
    String className = getClassNameFromReplName(replName);
    hooks.register(noteId, className, event, cmd);
  }

  /**
   * Unbind code from given hook event and given repl
   *
   * @param event    The type of event to hook to (pre_exec, post_exec)
   * @param replName Name of the interpreter
   */
  @Experimental
  public void unregisterHook(String event, String replName) {
    String className = getClassNameFromReplName(replName);
    hooks.unregister(null, className, event);
  }

  /**
   * unregisterHook() wrapper for current repl
   *
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public void unregisterHook(String event) {
    unregisterHook(event, interpreterContext.getReplName());
  }

  /**
   * Unbind code from given hook event and given note
   *
   * @param noteId The id of note
   * @param event  The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public void unregisterNoteHook(String noteId, String event) {
    String className = interpreterContext.getInterpreterClassName();
    hooks.unregister(noteId, className, event);
  }


  /**
   * Unbind code from given hook event, given note and given repl
   *
   * @param noteId   The id of note
   * @param event    The type of event to hook to (pre_exec, post_exec)
   * @param replName Name of the interpreter
   */
  @Experimental
  public void unregisterNoteHook(String noteId, String event, String replName) {
    String className = getClassNameFromReplName(replName);
    hooks.unregister(noteId, className, event);
  }


  /**
   * Add object into resource pool
   *
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
   *
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
   * Get object from resource pool
   * Search local process first and then the other processes
   *
   * @param name
   * @param clazz  The class of the returned value
   * @return null if resource not found
   */
  @ZeppelinApi
  public <T> T get(String name, Class<T> clazz) {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    Resource resource = resourcePool.get(name);
    if (resource != null) {
      return resource.get(clazz);
    } else {
      return null;
    }
  }

  /**
   * Remove object from resourcePool
   *
   * @param name
   */
  @ZeppelinApi
  public void remove(String name) {
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    resourcePool.remove(name);
  }

  /**
   * Check if resource pool has the object
   *
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
}
