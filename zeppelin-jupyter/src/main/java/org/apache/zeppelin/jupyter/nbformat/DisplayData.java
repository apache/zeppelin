package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 *
 */
public class DisplayData extends Output {

  @SerializedName("data")
  private Map<String, Object> data;

}
