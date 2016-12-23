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


import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.google.gson.annotations.SerializedName;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.annotation.Experimental;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 */
public abstract class Interpreter {

  /**
   * Opens interpreter. You may want to place your initialize routine here.
   * open() is called only once
   */
  @ZeppelinApi
  public abstract void open();

  /**
   * Closes interpreter. You may want to free your resources up here.
   * close() is called only once
   */
  @ZeppelinApi
  public abstract void close();

  /**
   * Run code and return result, in synchronous way.
   *
   * @param st statements to run
   * @param context
   * @return
   */
  @ZeppelinApi
  public abstract InterpreterResult interpret(String st, InterpreterContext context);

  /**
   * Optionally implement the canceling routine to abort interpret() method
   *
   * @param context
   */
  @ZeppelinApi
  public abstract void cancel(InterpreterContext context);

  /**
   * Dynamic form handling
   * see http://zeppelin.apache.org/docs/dynamicform.html
   *
   * @return FormType.SIMPLE enables simple pattern replacement (eg. Hello ${name=world}),
   *         FormType.NATIVE handles form in API
   */
  @ZeppelinApi
  public abstract FormType getFormType();

  /**
   * get interpret() method running process in percentage.
   *
   * @param context
   * @return number between 0-100
   */
  @ZeppelinApi
  public abstract int getProgress(InterpreterContext context);

  /**
   * Get completion list based on cursor position.
   * By implementing this method, it enables auto-completion.
   *
   * @param buf statements
   * @param cursor cursor position in statements
   * @return list of possible completion. Return empty list if there're nothing to return.
   */
  @ZeppelinApi
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return null;
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
   *
   * @return return scheduler instance.
   *         This method can be called multiple times and have to return the same instance.
   *         Can not return null.
   */
  @ZeppelinApi
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler("interpreter_" + this.hashCode());
  }

  public static Logger logger = LoggerFactory.getLogger(Interpreter.class);
  private InterpreterGroup interpreterGroup;
  private URL [] classloaderUrls;
  protected Properties property;
  private String userName;

  @ZeppelinApi
  public Interpreter(Properties property) {
    logger.debug("Properties: {}", property);
    this.property = property;
  }

  public void setProperty(Properties property) {
    this.property = property;
  }

  @ZeppelinApi
  public Properties getProperty() {
    Properties p = new Properties();
    p.putAll(property);

    RegisteredInterpreter registeredInterpreter = Interpreter.findRegisteredInterpreterByClassName(
        getClassName());
    if (null != registeredInterpreter) {
      Map<String, InterpreterProperty> defaultProperties = registeredInterpreter.getProperties();
      for (String k : defaultProperties.keySet()) {
        if (!p.containsKey(k)) {
          String value = defaultProperties.get(k).getValue();
          if (value != null) {
            p.put(k, defaultProperties.get(k).getValue());
          }
        }
      }
    }

    return p;
  }

  @ZeppelinApi
  public String getProperty(String key) {
    logger.debug("key: {}, value: {}", key, getProperty().getProperty(key));

    return getProperty().getProperty(key);
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
   * @param noteId - Note to bind hook to
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param cmd The code to be executed by the interpreter on given event
   */
  @Experimental
  public void registerHook(String noteId, String event, String cmd) {
    InterpreterHookRegistry hooks = interpreterGroup.getInterpreterHookRegistry();
    String className = getClassName();
    hooks.register(noteId, className, event, cmd);
  }

  /**
   * registerHook() wrapper for global scope
   * @param event The type of event to hook to (pre_exec, post_exec)
   * @param cmd The code to be executed by the interpreter on given event
   */
  @Experimental
  public void registerHook(String event, String cmd) {
    registerHook(null, event, cmd);
  }

  /**
   * Get the hook code
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
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public String getHook(String event) {
    return getHook(null, event);
  }

  /**
   * Unbind code from given hook event
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
   * @param event The type of event to hook to (pre_exec, post_exec)
   */
  @Experimental
  public void unregisterHook(String event) {
    unregisterHook(null, event);
  }
  
  @ZeppelinApi
  public Interpreter getInterpreterInTheSameSessionByClassName(String className) {
    synchronized (interpreterGroup) {
      for (List<Interpreter> interpreters : interpreterGroup.values()) {
        boolean belongsToSameNoteGroup = false;
        Interpreter interpreterFound = null;
        for (Interpreter intp : interpreters) {
          if (intp.getClassName().equals(className)) {
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

        if (belongsToSameNoteGroup) {
          return interpreterFound;
        }
      }
    }
    return null;
  }


  /**
   * Type of interpreter.
   */
  public static enum FormType {
    NATIVE, SIMPLE, NONE
  }

  /**
   * Represent registered interpreter class
   */
  public static class RegisteredInterpreter {
    //@SerializedName("interpreterGroup")
    private String group;
    //@SerializedName("interpreterName")
    private String name;
    //@SerializedName("interpreterClassName")
    private String className;
    private boolean defaultInterpreter;
    private Map<String, InterpreterProperty> properties;
    private Map<String, Object> editor;
    private String path;
    private InterpreterOption option;

    public RegisteredInterpreter(String name, String group, String className,
        Map<String, InterpreterProperty> properties) {
      this(name, group, className, false, properties);
    }

    public RegisteredInterpreter(String name, String group, String className,
        boolean defaultInterpreter, Map<String, InterpreterProperty> properties) {
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

    public Map<String, InterpreterProperty> getProperties() {
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
  }

  /**
   * Type of Scheduling.
   */
  public static enum SchedulingMode {
    FIFO, PARALLEL
  }

  public static Map<String, RegisteredInterpreter> registeredInterpreters = Collections
      .synchronizedMap(new HashMap<String, RegisteredInterpreter>());

  public static void register(String name, String className) {
    register(name, name, className);
  }

  public static void register(String name, String group, String className) {
    register(name, group, className, false, new HashMap<String, InterpreterProperty>());
  }

  public static void register(String name, String group, String className,
      Map<String, InterpreterProperty> properties) {
    register(name, group, className, false, properties);
  }

  public static void register(String name, String group, String className,
      boolean defaultInterpreter) {
    register(name, group, className, defaultInterpreter,
        new HashMap<String, InterpreterProperty>());
  }

  @Deprecated
  public static void register(String name, String group, String className,
      boolean defaultInterpreter, Map<String, InterpreterProperty> properties) {
    logger.warn("Static initialization is deprecated for interpreter {}, You should change it " +
                     "to use interpreter-setting.json in your jar or " +
                     "interpreter/{interpreter}/interpreter-setting.json", name);
    register(new RegisteredInterpreter(name, group, className, defaultInterpreter, properties));
  }

  public static void register(RegisteredInterpreter registeredInterpreter) {
    String interpreterKey = registeredInterpreter.getInterpreterKey();
    if (!registeredInterpreters.containsKey(interpreterKey)) {
      registeredInterpreters.put(interpreterKey, registeredInterpreter);
    } else {
      RegisteredInterpreter existInterpreter = registeredInterpreters.get(interpreterKey);
      if (!existInterpreter.getProperties().equals(registeredInterpreter.getProperties())) {
        logger.error("exist registeredInterpreter with the same key but has different settings.");
      }
    }
  }

  public static RegisteredInterpreter findRegisteredInterpreterByClassName(String className) {
    for (RegisteredInterpreter ri : registeredInterpreters.values()) {
      if (ri.getClassName().equals(className)) {
        return ri;
      }
    }
    return null;
  }
}
