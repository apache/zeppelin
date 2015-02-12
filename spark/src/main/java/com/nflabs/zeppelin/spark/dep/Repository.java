package com.nflabs.zeppelin.spark.dep;

/**
 *
 *
 */
public class Repository {
  private boolean snapshot = false;
  private String name;
  private String url;

  public Repository(String name){
    this.name = name;
  }

  public Repository url(String url) {
    this.url = url;
    return this;
  }

  public Repository snapshot() {
    snapshot = true;
    return this;
  }

  public boolean isSnapshot() {
    return snapshot;
  }

  public String getName() {
    return name;
  }

  public String getUrl() {
    return url;
  }
}
