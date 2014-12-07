package com.nflabs.zeppelin.interpreter;

import java.util.Properties;
import java.util.Random;

import com.nflabs.zeppelin.notebook.utility.IdHashes;

/**
 * Interpreter settings
 */
public class InterpreterSetting {
  private String id;
  private String name;
  private String group;
  private String description;
  private String className;
  private Properties properties;
  private transient Interpreter interpreter;
  
  public InterpreterSetting(String name,
      String group,
      String className,
      String description,
      Interpreter interpreter) {
    this.id = generateId();
    this.name = name;
    this.group = group;
    this.className = className;
    this.description = description;    
    this.properties = interpreter.getProperty();
    this.interpreter = interpreter;
  }

  public String id() {
    return id;
  }
  
  private String generateId() {
    return IdHashes.encode(System.currentTimeMillis() + new Random().nextInt());
  }  
  
  public Properties getProperties() {
    return properties;
  }
  
  public String getName() {
    return name;
  }
  
  public void setName(String name) {
    this.name = name;
  }
  
  public String getDescription() {
    return description;
  }
  
  public void setDescription(String desc) {
    this.description = desc;
  }
  
  public String getGroup() {
    return group;
  }
  
  public String getClassName() {
    return className;
  }
  
  public Interpreter getInterpreter() {
    return interpreter;
  }
  
  public void setInterpreter(Interpreter interpreter) {
    this.interpreter = interpreter;
    this.properties = interpreter.getProperty();
  }
}
