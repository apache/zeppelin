package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 *
 */
public class CodeCell extends Cell {

  @SerializedName("outputs")
  private List<Output> outputs;
}
