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


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.zeppelin.annotation.Experimental;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interface for interpreters.
 * If you want to implement new Zeppelin interpreter, extend this class
 *
 * Please see,
 * https://zeppelin.apache.org/docs/latest/development/writingzeppelininterpreter.html
 *
 * open(), close(), interpret() is three the most important method you need to implement.
 * cancel(), getProgress(), completion() is good to have
 * getFormType(), getScheduler() determine Zeppelin's behavior
 */
public abstract class Interpreter {

  /**
   * Opens interpreter. You may want to place your initialize routine here.
   * open() is called only once
   */
  @ZeppelinApi
  public abstract void open() throws InterpreterException;

  /**
   * Closes interpreter. You may want to free your resources up here.
   * close() is called only once
   */
  @ZeppelinApi
  public abstract void close() throws InterpreterException;

  /**
   * Run precode if exists.
   */
  @ZeppelinApi
  public InterpreterResult executePrecode(InterpreterContext interpreterContext)
      throws InterpreterException {
    String simpleName = this.getClass().getSimpleName();
    String precode = getProperty(String.format("zeppelin.%s.precode", simpleName));
    if (StringUtils.isNotBlank(precode)) {
      return interpret(precode, interpreterContext);
    }
    return null;
  }

  protected String interpolate(String cmd, ResourcePool resourcePool) {
    Pattern zVariablePattern = Pattern.compile("([^{}]*)([{]+[^{}]*[}]+)(.*)", Pattern.DOTALL);
    StringBuilder sb = new StringBuilder();
    Matcher m;
    String st = cmd;
    while ((m = zVariablePattern.matcher(st)).matches()) {
      sb.append(m.group(1));
      String varPat = m.group(2);
      if (varPat.matches("[{][^{}]+[}]")) {
        // substitute {variable} only if 'variable' has a value ...
        Resource resource = resourcePool.get(varPat.substring(1, varPat.length() - 1));
        Object variableValue = resource == null ? null : resource.get();
        if (variableValue != null)
          sb.append(variableValue);
        else
          return cmd;
      } else if (varPat.matches("[{]{2}[^{}]+[}]{2}")) {
        // escape {{text}} ...
        sb.append("{").append(varPat.substring(2, varPat.length() - 2)).append("}");
      } else {
        // mismatched {{ }} or more than 2 braces ...
        return cmd;
      }
      st = m.group(3);
    }
    sb.append(st);
    return sb.toString();
  }

  /**
   * Run code and return result, in synchronous way.
   *
   * @param st statements to run
   */
  @ZeppelinApi
  public abstract InterpreterResult interpret(String st,
                                              InterpreterContext context)
      throws InterpreterException;

  /**
   * Optionally implement the canceling routine to abort interpret() method
   */
  @ZeppelinApi
  public abstract void cancel(InterpreterContext context) throws InterpreterException;

  /**
   * Dynamic form handling
   * see http://zeppelin.apache.org/docs/dynamicform.html
   *
   * @return FormType.SIMPLE enables simple pattern replacement (eg. Hello ${name=world}),
   * FormType.NATIVE handles form in API
   */
  @ZeppelinApi
  public abstract FormType getFormType() throws InterpreterException;

  /**
   * get interpret() method running process in percentage.
   *
   * @return number between 0-100
   */
  @ZeppelinApi
  public abstract int getProgress(InterpreterContext context) throws InterpreterException;

  /**
   * Get completion list based on cursor position.
   * By implementing this method, it enables auto-completion.
   *
   * @param buf statements
   * @param cursor cursor position in statements
   * @param interpreterContext
   * @return list of possible completion. Return empty list if there're nothing to return.
   */
  @ZeppelinApi
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) throws InterpreterException {
    return Collections.emptyList();
  }

  /**
   * Interpreter can implements it's own scheduler by overriding this method.
   * There're two default scheduler provided, FIFO, Parallel.
   * If your interpret() can handle concurrent request, use Parallel or use FIFO.
   *
   * You can get default scheduler by using
   * SchedulerFactory.singleton().createOrGetFIFOScheduler()
   * SchedulerFactory.singleton().createOrGetParallelScheduler()
   *
   * @return return scheduler instance. This method can be called multiple times and have to return
   * the same instance. Can not return null.
   */
  @ZeppelinApi
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler("interpreter_" + this.hashCode());
  }

  public static Logger logger = LoggerFactory.getLogger(Interpreter.class);
  private InterpreterGroup interpreterGroup;
  private URL[] classloaderUrls;
  protected Properties properties;
  protected String userName;

  @ZeppelinApi
  public Interpreter(Properties properties) {
    this.properties = properties;
  }

  public void setProperties(Properties properties) {
    this.properties = properties;
  }

  @ZeppelinApi
  public Properties getProperties() {
    Properties p = new Properties();
    p.putAll(properties);
    replaceContextParameters(p);
    return p;
  }

  @ZeppelinApi
  public String getProperty(String key) {
    logger.debug("key: {}, value: {}", key, getProperties().getProperty(key));

    return getProperties().getProperty(key);
  }

  @ZeppelinApi
  public String getProperty(String key, String defaultValue) {
    logger.debug("key: {}, value: {}", key, getProperties().getProperty(key, defaultValue));

    return getProperties().getProperty(key, defaultValue);
  }

  @ZeppelinApi
  public void setProperty(String key, String value) {
    properties.setProperty(key, value);
  }

  public String getClassName() {
    return this.getClass().getName();
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getUserName() {
    return this.userName;
  }

  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    this.interpreterGroup = interpreterGroup;
  }

  @ZeppelinApi
  public InterpreterGroup getInterpreterGroup() {
    return this.interpreterGroup;
  }

  public URL[] getClassloaderUrls() {
    return classloaderUrls;
  }

  public void setClassloaderUrls(URL[] classloaderUrls) {
    this.classloaderUrls = classloaderUrls;
  }

  /**
   * General function to register hook event
   *
   * @param noteId - Note to bind hook to
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param cmd The code to be executed by the interpreter on given event
   */
  @Experimental
  public void registerHook(String noteId, String event, String cmd) throws InvalidHookException {
    InterpreterHookRegistry hooks = interpreterGroup.getInterpreterHookRegistry();
    String className = getClassName();
    hooks.register(noteId, className, event, cmd);
  }

  /**
   * registerHook() wrapper for global scope
   *
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param cmd The code to be executed by the interpreter on given event
   */
  @Experimental
  public void registerHook(String event, String cmd) throws InvalidHookException {
    registerHook(null, event, cmd);
  }

  /**
   * Get the hook code
   *
   * @param noteId - Note to bind hook to
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public String getHook(String noteId, String event) {
    InterpreterHookRegistry hooks = interpreterGroup.getInterpreterHookRegistry();
    String className = getClassName();
    return hooks.get(noteId, className, event);
  }

  /**
   * getHook() wrapper for global scope
   *
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public String getHook(String event) {
    return getHook(null, event);
  }

  /**
   * Unbind code from given hook event
   *
   * @param noteId - Note to bind hook to
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public void unregisterHook(String noteId, String event) {
    InterpreterHookRegistry hooks = interpreterGroup.getInterpreterHookRegistry();
    String className = getClassName();
    hooks.unregister(noteId, className, event);
  }

  /**
   * unregisterHook() wrapper for global scope
   *
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public void unregisterHook(String event) {
    unregisterHook(null, event);
  }

  @ZeppelinApi
  public <T> T getInterpreterInTheSameSessionByClassName(Class<T> interpreterClass, boolean open)
      throws InterpreterException {
    synchronized (interpreterGroup) {
      for (List<Interpreter> interpreters : interpreterGroup.values()) {
        boolean belongsToSameNoteGroup = false;
        Interpreter interpreterFound = null;
        for (Interpreter intp : interpreters) {
          if (intp.getClassName().equals(interpreterClass.getName())) {
            interpreterFound = intp;
          }

          Interpreter p = intp;
          while (p instanceof WrappedInterpreter) {
            p = ((WrappedInterpreter) p).getInnerInterpreter();
          }
          if (this == p) {
            belongsToSameNoteGroup = true;
          }
        }

        if (belongsToSameNoteGroup && interpreterFound != null) {
          LazyOpenInterpreter lazy = null;
          T innerInterpreter = null;
          while (interpreterFound instanceof WrappedInterpreter) {
            if (interpreterFound instanceof LazyOpenInterpreter) {
              lazy = (LazyOpenInterpreter) interpreterFound;
            }
            interpreterFound = ((WrappedInterpreter) interpreterFound).getInnerInterpreter();
          }
          innerInterpreter = (T) interpreterFound;

          if (lazy != null && open) {
            lazy.open();
          }
          return innerInterpreter;
        }
      }
    }
    return null;
  }

  public <T> T getInterpreterInTheSameSessionByClassName(Class<T> interpreterClass)
      throws InterpreterException {
    return getInterpreterInTheSameSessionByClassName(interpreterClass, true);
  }

  /**
   * Replace markers #{contextFieldName} by values from {@link InterpreterContext} fields
   * with same name and marker #{user}. If value == null then replace by empty string.
   */
  private void replaceContextParameters(Properties properties) {
    InterpreterContext interpreterContext = InterpreterContext.get();
    if (interpreterContext != null) {
      String markerTemplate = "#\\{%s\\}";
      List<String> skipFields = Arrays.asList("paragraphTitle", "paragraphId", "paragraphText");
      List typesToProcess = Arrays.asList(String.class, Double.class, Float.class, Short.class,
          Byte.class, Character.class, Boolean.class, Integer.class, Long.class);
      for (String key : properties.stringPropertyNames()) {
        String p = properties.getProperty(key);
        if (StringUtils.isNotEmpty(p)) {
          for (Field field : InterpreterContext.class.getDeclaredFields()) {
            Class clazz = field.getType();
            if (!skipFields.contains(field.getName()) && (typesToProcess.contains(clazz)
                || clazz.isPrimitive())) {
              Object value = null;
              try {
                value = FieldUtils.readField(field, interpreterContext, true);
              } catch (Exception e) {
                logger.error("Cannot read value of field {0}", field.getName());
              }
              p = p.replaceAll(String.format(markerTemplate, field.getName()),
                  value != null ? value.toString() : StringUtils.EMPTY);
            }
          }
          p = p.replaceAll(String.format(markerTemplate, "user"),
              StringUtils.defaultString(userName, StringUtils.EMPTY));
          properties.setProperty(key, p);
        }
      }
    }
  }

  /**
   * Type of interpreter.
   */
  public enum FormType {
    NATIVE, SIMPLE, NONE
  }

  /**
   * Represent registered interpreter class
   */
  public static class RegisteredInterpreter {

    private String group;
    private String name;
    private String className;
    private boolean defaultInterpreter;
    private Map<String, DefaultInterpreterProperty> properties;
    private Map<String, Object> editor;
    private Map<String, Object> config;
    private String path;
    private InterpreterOption option;
    private InterpreterRunner runner;

    public RegisteredInterpreter(String name, String group, String className,
        Map<String, DefaultInterpreterProperty> properties) {
      this(name, group, className, false, properties);
    }

    public RegisteredInterpreter(String name, String group, String className,
        boolean defaultInterpreter, Map<String, DefaultInterpreterProperty> properties) {
      super();
      this.name = name;
      this.group = group;
      this.className = className;
      this.defaultInterpreter = defaultInterpreter;
      this.properties = properties;
      this.editor = new HashMap<>();
    }

    public String getName() {
      return name;
    }

    public String getGroup() {
      return group;
    }

    public String getClassName() {
      return className;
    }

    public boolean isDefaultInterpreter() {
      return defaultInterpreter;
    }

    public void setDefaultInterpreter(boolean defaultInterpreter) {
      this.defaultInterpreter = defaultInterpreter;
    }

    public Map<String, DefaultInterpreterProperty> getProperties() {
      return properties;
    }

    public Map<String, Object> getEditor() {
      return editor;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }

    public String getInterpreterKey() {
      return getGroup() + "." + getName();
    }

    public InterpreterOption getOption() {
      return option;
    }

    public InterpreterRunner getRunner() {
      return runner;
    }

    public Map<String, Object> getConfig() {
      return config;
    }

    public void setConfig(Map<String, Object> config) {
      this.config = config;
    }
  }

  /**
   * Type of Scheduling.
   */
  public enum SchedulingMode {
    FIFO, PARALLEL
  }

}
