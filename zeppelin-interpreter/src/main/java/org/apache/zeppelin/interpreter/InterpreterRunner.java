package org.apache.zeppelin.interpreter;

import com.google.gson.annotations.SerializedName;

/**
 * Interpreter runner path
 */
public class InterpreterRunner {

  @SerializedName("linux")
  private String linuxPath;
  @SerializedName("win")
  private String winPath;

  public String getPath() {
    return System.getProperty("os.name").startsWith("Windows") ? winPath : linuxPath;
  }
}
