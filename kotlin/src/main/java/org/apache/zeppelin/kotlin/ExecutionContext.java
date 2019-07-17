package org.apache.zeppelin.kotlin;

public class ExecutionContext {
  public String msg;
  public StringBuilder sb;

  public ExecutionContext(String msg) {
    this.msg = msg;
    this.sb = new StringBuilder();
  }
}
