package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 *
 */
public abstract class Cell {

  @SerializedName("cell_type")
  private String cellType;

  @SerializedName("metadata")
  private CellMetadata metadata;

  @SerializedName("source")
  private List<String> source;
}
