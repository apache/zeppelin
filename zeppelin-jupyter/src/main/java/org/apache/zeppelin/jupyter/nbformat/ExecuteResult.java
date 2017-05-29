package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 *
 */
public class ExecuteResult extends Output {

  @SerializedName("execution_count")
  private int executionCount;

  @SerializedName("data")
  private Map<String, Object> data;

}
