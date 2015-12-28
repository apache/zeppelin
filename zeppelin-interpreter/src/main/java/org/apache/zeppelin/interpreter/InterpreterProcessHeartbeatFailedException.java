package org.apache.zeppelin.interpreter;

/**
 * InterpreterException for noticing that interpreter process failed to heartbeat
 */
public class InterpreterProcessHeartbeatFailedException extends InterpreterException {
  public InterpreterProcessHeartbeatFailedException(Throwable e) {
    super(e);
  }

  public InterpreterProcessHeartbeatFailedException(String m) {
    super(m);
  }
}
