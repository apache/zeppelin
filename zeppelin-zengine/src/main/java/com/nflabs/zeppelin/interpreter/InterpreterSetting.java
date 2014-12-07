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
  private String description;
  private String className;
  private Properties properties;
  private transient Interpreter interpreter;
  
  public InterpreterSetting(String name,
      String description,
      String className,
      Interpreter interpreter) {
    this.id = generateId();
    this.name = name;
    this.description = description;
    this.className = className;
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
