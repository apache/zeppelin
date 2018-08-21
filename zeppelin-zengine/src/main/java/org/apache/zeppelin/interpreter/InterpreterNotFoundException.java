package org.apache.zeppelin.interpreter;

/** Exception for no interpreter is found */
public class InterpreterNotFoundException extends InterpreterException {

  public InterpreterNotFoundException() {}

  public InterpreterNotFoundException(String message) {
    super(message);
  }

  public InterpreterNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public InterpreterNotFoundException(Throwable cause) {
    super(cause);
  }
}
