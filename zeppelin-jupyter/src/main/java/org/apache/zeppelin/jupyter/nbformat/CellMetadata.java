package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 *
 */
public class CellMetadata {

  @SerializedName("name")
  private String name;

  @SerializedName("tags")
  private List<String> tags;

}
