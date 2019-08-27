package org.apache.zeppelin.ksql;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Query {
  @JsonProperty("ksql")
  public String query = "";

  public Query(final String query) {
    this.query = query;
  }
}
