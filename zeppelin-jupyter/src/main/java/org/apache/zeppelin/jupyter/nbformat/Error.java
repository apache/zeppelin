package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 *
 */
public class Error extends Output {
  @SerializedName("ename")
  private String ename;

  @SerializedName("evalue")
  private String evalue;

  @SerializedName("traceback")
  private List<String> traceback;
}
