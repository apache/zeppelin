package com.nflabs.zeppelin.interpreter;

/**
 * Represent property of interpreter 
 */
public class InterpreterProperty {
  String defaultValue;
  String description;

  public InterpreterProperty(String defaultValue,
      String description) {
    super();
    this.defaultValue = defaultValue;
    this.description = description;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
