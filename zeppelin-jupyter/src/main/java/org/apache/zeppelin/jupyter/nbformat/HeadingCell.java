package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;

public class HeadingCell extends Cell {

  @SerializedName("level")
  private int level;

  public int getLevel() {
    return level;
  }
}
