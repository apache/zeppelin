package com.nflabs.zeppelin.interpreter;

/**
 * WrappedInterpreter
 */
public interface WrappedInterpreter {
  public Interpreter getInnerInterpreter();
}
