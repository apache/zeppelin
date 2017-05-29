package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;

/**
 *
 */
public class RawCellMetadata extends CellMetadata {

  @SerializedName("format")
  private String format;
}
