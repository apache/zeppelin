package org.apache.zeppelin.interpreter;

import com.google.gson.annotations.SerializedName;

/**
 * Interpreter runner path
 */
public class InterpreterRunner {

  @SerializedName("linux")
  String linuxPath;
  @SerializedName("win")
  String winPath;

  public String getPath() {
    return System.getProperty("os.name").startsWith("Windows") ? winPath : linuxPath;
  }
}
