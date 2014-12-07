package com.nflabs.zeppelin.interpreter;


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
  private Properties property;
  private InterpreterGroup interpreterGroup;

  public Interpreter(Properties property) {
    this.property = property;
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
    
    public RegisteredInterpreter(String name, String group, String className) {
      super();
      this.name = name;
      this.group = group;
      this.className = className;
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
    registeredInterpreters.put(name, new RegisteredInterpreter(name, group, className));
  }
  
  public abstract void open();

  public abstract void close();

  public abstract Object getValue(String name);

  public abstract InterpreterResult interpret(String st);

  public abstract void cancel();

  public abstract void bindValue(String name, Object o);

  public abstract FormType getFormType();

  public abstract int getProgress();

  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler("interpreter_" + this.hashCode());
  }

  public void destroy() {
    getScheduler().stop();
  }

  public abstract List<String> completion(String buf, int cursor);

  public Properties getProperty() {
    return property;
  }

  public void setProperty(Properties property) {
    this.property = property;
  }
  
  public InterpreterGroup getInterpreterGroup() {
    return interpreterGroup;
  }
  
  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    this.interpreterGroup = interpreterGroup;
  }
  
  public String getClassName() {
    return this.getClass().getName();
  }
}
