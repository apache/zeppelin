package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;

/**
 *
 */
public class Kernelspec {

  @SerializedName("name")
  private String name;

  @SerializedName("display_name")
  private String displayName;
}
