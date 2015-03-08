package com.nflabs.zeppelin.interpreter;


import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

/**
 * Interface for interpreters.
 */
public abstract class Interpreter {
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
      if (!p.contains(k)) {
        String value = defaultProperties.get(k).getDefaultValue();
        if (value != null) {
          p.put(k, defaultProperties.get(k).getDefaultValue());
        }
      }
    }

    return property;
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
    registeredInterpreters.put(name, new RegisteredInterpreter(name, group, className, properties));
  }

  public static RegisteredInterpreter findRegisteredInterpreterByClassName(String className) {
    for (RegisteredInterpreter ri : registeredInterpreters.values()) {
      if (ri.getClassName().equals(className)) {
        return ri;
      }
    }
    return null;
  }

  public abstract void open();

  public abstract void close();

  public abstract Object getValue(String name);

  public abstract InterpreterResult interpret(String st, InterpreterContext context);

  public abstract void cancel(InterpreterContext context);

  public abstract void bindValue(String name, Object o);

  public abstract FormType getFormType();

  public abstract int getProgress(InterpreterContext context);

  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler("interpreter_" + this.hashCode());
  }

  public void destroy() {
    getScheduler().stop();
  }

  public abstract List<String> completion(String buf, int cursor);

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
}
