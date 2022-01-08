package org.apache.zeppelin.elasticsearch.client;

public enum ElasticsearchClientType {
  HTTP(true), HTTPS(true), TRANSPORT(false), UNKNOWN(false);

  private final boolean isHttp;

  ElasticsearchClientType(boolean isHttp) {
    this.isHttp = isHttp;
  }

  public boolean isHttp() {
    return isHttp;
  }
}
