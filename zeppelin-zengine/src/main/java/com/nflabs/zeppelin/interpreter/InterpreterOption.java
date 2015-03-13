package com.nflabs.zeppelin.interpreter;

/**
 *
 */
public class InterpreterOption {
  boolean remote;

  public InterpreterOption() {
    remote = false;
  }

  public InterpreterOption(boolean remote) {
    this.remote = remote;
  }

  public boolean isRemote() {
    return remote;
  }

  public void setRemote(boolean remote) {
    this.remote = remote;
  }
}
