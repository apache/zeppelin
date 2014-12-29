package com.nflabs.zeppelin.interpreter;

import java.util.HashMap;
import java.util.Map;

/**
 * InterpreterPropertyBuilder 
 */
public class InterpreterPropertyBuilder {
  Map<String, InterpreterProperty> properties = new HashMap<String, InterpreterProperty>();
  
  public InterpreterPropertyBuilder add(String name, String defaultValue, String description){
    properties.put(name, new InterpreterProperty(defaultValue, description));
    return this;
  }
  
  public Map<String, InterpreterProperty> build(){
    return properties;
  }
}
