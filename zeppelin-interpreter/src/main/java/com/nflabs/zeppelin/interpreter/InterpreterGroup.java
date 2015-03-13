package com.nflabs.zeppelin.interpreter;

import java.util.LinkedList;
import java.util.Properties;
import java.util.Random;

/**
 * InterpreterGroup is list of interpreters in the same group.
 * And unit of interpreter instantiate, restart, bind, unbind.
 */
public class InterpreterGroup extends LinkedList<Interpreter>{
  String id;

  private static String generateId() {
    return "InterpreterGroup_" + System.currentTimeMillis() + "_"
           + new Random().nextInt();
  }

  public String getId() {
    synchronized (this) {
      if (id == null) {
        id = generateId();
      }
      return id;
    }
  }


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
