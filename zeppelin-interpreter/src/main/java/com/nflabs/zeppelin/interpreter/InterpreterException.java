package com.nflabs.zeppelin.interpreter;

/**
 * Runtime Exception for interpreters.
 * 
 */
public class InterpreterException extends RuntimeException {

  public InterpreterException(Throwable e) {
    super(e);
  }

  public InterpreterException(String m) {
    super(m);
  }

}
