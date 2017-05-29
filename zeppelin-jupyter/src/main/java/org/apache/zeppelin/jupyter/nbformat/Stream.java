package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 *
 */
public class Stream extends Output {

  @SerializedName("name")
  private String name;

  @SerializedName("text")
  private List<String> text;

}
