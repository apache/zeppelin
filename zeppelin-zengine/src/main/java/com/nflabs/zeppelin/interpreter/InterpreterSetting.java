package com.nflabs.zeppelin.interpreter;

import java.util.Map;
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
  private Properties properties;
  private InterpreterGroup interpreterGroup;
  
  public InterpreterSetting(String id, String name,
      String group,
      InterpreterGroup interpreterGroup) {
    this.id = id;
    this.name = name;
    this.group = group;
    this.properties = interpreterGroup.getProperty();
    this.interpreterGroup = interpreterGroup;
  }

  public InterpreterSetting(String name,
      String group,
      InterpreterGroup interpreterGroup) {
    this(generateId(), name, group, interpreterGroup);
  }

  public String id() {
    return id;
  }

  private static String generateId() {
    return IdHashes.encode(System.currentTimeMillis() + new Random().nextInt());
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

  public InterpreterGroup getInterpreterGroup() {
    return interpreterGroup;
  }

  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    this.interpreterGroup = interpreterGroup;
    this.properties = interpreterGroup.getProperty();
  }

  public Properties getProperties() {
    return properties;
  }
}
