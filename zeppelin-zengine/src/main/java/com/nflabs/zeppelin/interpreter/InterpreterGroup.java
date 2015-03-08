package com.nflabs.zeppelin.interpreter;

import java.util.LinkedList;
import java.util.Properties;

/**
 * InterpreterGroup is list of interpreters in the same group.
 * And unit of interpreter instantiate, restart, bind, unbind. 
 */
public class InterpreterGroup extends LinkedList<Interpreter>{

  public Properties getProperty() {
    Properties p = new Properties();
    for (Interpreter intp : this) {
      p.putAll(intp.getProperty());
    }
    return p;
  }

  public void close() {
    for (Interpreter intp : this) {
      intp.close();
    }    
  }

  public void destroy() {
    for (Interpreter intp : this) {
      intp.destroy();
    }    
  }
}
