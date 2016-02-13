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

import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface for interpreters.
 * If you want to implement new Zeppelin interpreter, extend this class
 *
 * Please see,
 * http://zeppelin.incubator.apache.org/docs/development/writingzeppelininterpreter.html
 *
 * open(), close(), interpreter() is three the most important method you need to implement.
 * cancel(), getProgress(), completion() is good to have
 * getFormType(), getScheduler() determine Zeppelin's behavior
 *
 */
public abstract class Interpreter {

  /**
   * Opens interpreter. You may want to place your initialize routine here.
   * open() is called only once
   */
  public abstract void open();

  /**
   * Closes interpreter. You may want to free your resources up here.
   * close() is called only once
   */
  public abstract void close();

  /**
   * Run code and return result, in synchronous way.
   *
   * @param st statements to run
   * @param context
   * @return
   */
  public abstract InterpreterResult interpret(String st, InterpreterContext context);

  /**
   * Optionally implement the canceling routine to abort interpret() method
   *
   * @param context
   */
  public abstract void cancel(InterpreterContext context);

  /**
   * Dynamic form handling
   * see http://zeppelin.incubator.apache.org/docs/dynamicform.html
   *
   * @return FormType.SIMPLE enables simple pattern replacement (eg. Hello ${name=world}),
   *         FormType.NATIVE handles form in API
   */
  public abstract FormType getFormType();

  /**
   * get interpret() method running process in percentage.
   *
   * @param context
   * @return number between 0-100
   */
  public abstract int getProgress(InterpreterContext context);

  /**
   * Get completion list based on cursor position.
   * By implementing this method, it enables auto-completion.
   *
   * @param buf statements
   * @param cursor cursor position in statements
   * @return list of possible completion. Return empty list if there're nothing to return.
   */
  public abstract List<String> completion(String buf, int cursor);

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
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler("interpreter_" + this.hashCode());
  }

  /**
   * Called when interpreter is no longer used.
   */
  public void destroy() {
    Scheduler scheduler = getScheduler();
    if (scheduler != null) {
      scheduler.stop();
    }
  }





  static Logger logger = LoggerFactory.getLogger(Interpreter.class);
  private InterpreterGroup interpreterGroup;
  private URL [] classloaderUrls;
  protected Properties property;

  public Interpreter(Properties property) {
    this.property = property;
  }

  public void setProperty(Properties property) {
    this.property = property;
  }

  public Properties getProperty() {
    Properties p = new Properties();
    p.putAll(property);

    Map<String, InterpreterProperty> defaultProperties = Interpreter
        .findRegisteredInterpreterByClassName(getClassName()).getProperties();
    for (String k : defaultProperties.keySet()) {
      if (!p.containsKey(k)) {
        String value = defaultProperties.get(k).getDefaultValue();
        if (value != null) {
          p.put(k, defaultProperties.get(k).getDefaultValue());
        }
      }
    }

    return p;
  }

  public String getProperty(String key) {
    if (property.containsKey(key)) {
      return property.getProperty(key);
    }

    Map<String, InterpreterProperty> defaultProperties = Interpreter
        .findRegisteredInterpreterByClassName(getClassName()).getProperties();
    if (defaultProperties.containsKey(key)) {
      return defaultProperties.get(key).getDefaultValue();
    }

    return null;
  }


  public String getClassName() {
    return this.getClass().getName();
  }

  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    this.interpreterGroup = interpreterGroup;
  }

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
   * Type of interpreter.
   */
  public static enum FormType {
    NATIVE, SIMPLE, NONE
  }

  /**
   * Represent registered interpreter class
   */
  public static class RegisteredInterpreter {
    private String name;
    private String group;
    private String className;
    private Map<String, InterpreterProperty> properties;
    private String path;

    public RegisteredInterpreter(String name, String group, String className,
        Map<String, InterpreterProperty> properties) {
      super();
      this.name = name;
      this.group = group;
      this.className = className;
      this.properties = properties;
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

    public Map<String, InterpreterProperty> getProperties() {
      return properties;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
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
    register(name, group, className, new HashMap<String, InterpreterProperty>());
  }

  public static void register(String name, String group, String className,
      Map<String, InterpreterProperty> properties) {
    registeredInterpreters.put(group + "." + name, new RegisteredInterpreter(
        name, group, className, properties));
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
