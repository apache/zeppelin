package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;

/**
 *
 */
public abstract class Output {

  @SerializedName("output_type")
  private String outputType;
}
