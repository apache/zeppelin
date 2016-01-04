package org.apache.zeppelin.interpreter;

/**
 * Listen InterpreterOutput buffer flush
 */
public interface InterpreterOutputNewlineListener {
  /**
   * called when newline is detected
   * @param line
   */
  public void onNewLineDetected(byte[] line);
}
