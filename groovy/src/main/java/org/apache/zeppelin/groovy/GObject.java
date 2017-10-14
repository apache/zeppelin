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
package org.apache.zeppelin.groovy;


import java.io.StringWriter;

import org.slf4j.Logger;

import java.util.Properties;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;

import groovy.xml.MarkupBuilder;
import groovy.lang.Closure;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.ui.OptionInput.ParamOption;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.interpreter.RemoteWorksController;
import org.apache.zeppelin.interpreter.InterpreterException;

/**
 * Groovy interpreter for Zeppelin.
 */
public class GObject extends groovy.lang.GroovyObjectSupport {

  Logger log;
  StringWriter out;
  Properties props;
  InterpreterContext interpreterContext;
  Map<String, Object> bindings;


  public GObject(Logger log, StringWriter out, Properties p, InterpreterContext ctx,
      Map<String, Object> bindings) {
    this.log = log;
    this.out = out;
    this.interpreterContext = ctx;
    this.props = p;
    this.bindings = bindings;
  }

  public Object getProperty(String key) {
    if ("log".equals(key)) {
      return log;
    }
    return props.getProperty(key);
  }

  public void setProperty(String key, Object value) {
    throw new RuntimeException("Set properties not supported: " + key + "=" + value);
  }

  public Properties getProperties() {
    return props;
  }

  private void startOutputType(String type) {
    StringBuffer sb = out.getBuffer();
    if (sb.length() > 0) {
      if (sb.length() < type.length() || !type.equals(sb.substring(0, type.length()))) {
        log.error("try to start output `" + type + "` after non-" + type + " started");
      }
    } else {
      out.append(type);
      out.append('\n');
    }
  }

  /**
   * returns gui object
   */
  public GUI getGui() {
    return interpreterContext.getGui();
  }

  @ZeppelinApi
  public Object input(String name) {
    return input(name, "");
  }

  @ZeppelinApi
  public Object input(String name, Object defaultValue) {
    return getGui().input(name, defaultValue);
  }

  private ParamOption[] toParamOptions(Map<Object, String> options) {
    ParamOption[] paramOptions = new ParamOption[options.size()];
    int i = 0;
    for (Map.Entry<Object, String> e : options.entrySet()) {
      paramOptions[i++] = new ParamOption(e.getKey(), e.getValue());
    }
    return paramOptions;
  }

  @ZeppelinApi
  public Object select(String name, Map<Object, String> options) {
    return select(name, "", options);
  }

  @ZeppelinApi
  public Object select(String name, Object defaultValue, Map<Object, String> options) {
    return getGui().select(name, defaultValue, toParamOptions(options));
  }

  @ZeppelinApi
  public Collection<Object> checkbox(String name, Map<Object, String> options) {
    return checkbox(name, options.keySet(), options);
  }

  @ZeppelinApi
  public Collection<Object> checkbox(String name, Collection<Object> defaultChecked,
      Map<Object, String> options) {
    return getGui().checkbox(name, defaultChecked, toParamOptions(options));
  }


  /**
   * Returns shared variable if it was previously set. The same as getting groovy script variables
   * but this method will return null if script variable not assigned. To understand groovy script
   * variables see groovy.transform.Field annotation for more information.
   *
   * @see #put
   */
  public Object get(String varName) {
    return bindings.get(varName);
  }

  /**
   * Returns script (shared) variable value but if value was not set returns default value. The same
   * as getting groovy script variables but this method will return default value if script variable
   * not assigned. To understand groovy script variables see groovy.transform.Field annotation for
   * more information.
   *
   * @see #put
   */
  public Object get(String varName, Object defValue) {
    return bindings.containsKey(varName) ? bindings.get(varName) : defValue;
  }

  /**
   * Sets a new value to interpreter's shared variables.
   * Could be set by <code>put('varName', newValue )</code>
   * or by just assigning <code>varName = value</code> without declaring a variable.
   */
  public Object put(String varName, Object newValue) {
    return bindings.put(varName, newValue);
  }

  /**
   * starts or continues rendering html/angular and returns MarkupBuilder to build html.
   * <pre> g.html().with{
   *  h1("hello")
   *  h2("world")
   * }</pre>
   */
  public MarkupBuilder html() {
    startOutputType("%angular");
    return new MarkupBuilder(out);
  }

  /**
   * starts or continues rendering table rows
   *
   * @param obj: 1. List(rows) of List(columns) where first line is a header
   */
  public void table(Object obj) {
    if (obj == null) {
      return;
    }
    StringBuffer sb = out.getBuffer();
    startOutputType("%table");
    if (obj instanceof groovy.lang.Closure) {
      //if closure run and get result collection
      obj = ((Closure) obj).call();
    }
    if (obj instanceof Collection) {
      int count = 0;
      for (Object row : ((Collection) obj)) {
        count++;
        boolean rowStarted = false;
        if (row instanceof Collection) {
          for (Object field : ((Collection) row)) {
            if (rowStarted) {
              sb.append('\t');
            }
            sb.append(field);
            rowStarted = true;
          }
        } else {
          sb.append(row);
        }
        sb.append('\n');
      }
    } else {
      throw new RuntimeException("Not supported table value :" + obj.getClass());
    }
  }

  private AngularObject getAngularObject(String name) {
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
   *
   * @param name variable name
   * @return value
   */
  public Object angular(String name) {
    AngularObject ao = getAngularObject(name);
    if (ao == null) {
      return null;
    } else {
      return ao.get();
    }
  }

  @SuppressWarnings("unchecked")
  public void angularBind(String name, Object o, String noteId) {
    AngularObjectRegistry registry = interpreterContext.getAngularObjectRegistry();

    if (registry.get(name, noteId, null) == null) {
      registry.add(name, o, noteId, null);
    } else {
      registry.get(name, noteId, null).set(o);
    }
  }

  /**
   * Create angular variable in notebook scope and bind with front end Angular display system.
   * If variable exists, it'll be overwritten.
   *
   * @param name name of the variable
   * @param o value
   */
  public void angularBind(String name, Object o) {
    angularBind(name, o, interpreterContext.getNoteId());
  }

  /*------------------------------------------RUN----------------------------------------*/
  @ZeppelinApi
  public List<InterpreterContextRunner> getInterpreterContextRunner(String noteId,
      String paragraphId, InterpreterContext interpreterContext) {
    RemoteWorksController remoteWorksController = interpreterContext.getRemoteWorksController();
    if (remoteWorksController != null) {
      return remoteWorksController.getRemoteContextRunner(noteId, paragraphId);
    }
    return new LinkedList<InterpreterContextRunner>();
  }

  @ZeppelinApi
  public List<InterpreterContextRunner> getInterpreterContextRunner(String noteId,
      InterpreterContext interpreterContext) {
    RemoteWorksController remoteWorksController = interpreterContext.getRemoteWorksController();
    if (remoteWorksController != null) {
      return remoteWorksController.getRemoteContextRunner(noteId);
    }
    return new LinkedList<InterpreterContextRunner>();
  }

  /**
   * Run paragraph by id
   */
  @ZeppelinApi
  public void run(String noteId, String paragraphId) {
    run(noteId, paragraphId, interpreterContext);
  }

  /**
   * Run paragraph by id
   */
  @ZeppelinApi
  public void run(String paragraphId) {
    String noteId = interpreterContext.getNoteId();
    run(noteId, paragraphId, interpreterContext);
  }

  /**
   * Run paragraph by id
   */
  @ZeppelinApi
  public void run(String noteId, String paragraphId, InterpreterContext context) {
    if (paragraphId.equals(context.getParagraphId())) {
      throw new RuntimeException("Can not run current Paragraph");
    }
    List<InterpreterContextRunner> runners = getInterpreterContextRunner(noteId, paragraphId,
        context);
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
   * Run all paragraphs. except this.
   */
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


}
