package org.apache.zeppelin.elasticsearch.client;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

import static org.apache.zeppelin.elasticsearch.client.ElasticsearchClientType.TRANSPORT;
import static org.apache.zeppelin.elasticsearch.client.ElasticsearchClientType.UNKNOWN;
import static org.apache.zeppelin.elasticsearch.client.ElasticsearchClientType.valueOf;
import static org.apache.zeppelin.elasticsearch.client.ElasticsearchClientType.values;

public class ElasticsearchClientTypeBuilder {

  private static final ElasticsearchClientType DEFAULT_ELASTICSEARCH_CLIENT_TYPE = TRANSPORT;

  public static Build withPropertyValue(String propertyValue) {
    return new Builder(propertyValue);
  }

  public interface Build {
    ElasticsearchClientType build();
  }

  private static class Builder implements Build {
    private final String propertyValue;

    private Builder(String propertyValue) {
      this.propertyValue = propertyValue;
    }

    @Override
    public ElasticsearchClientType build() {
      boolean isEmpty = StringUtils.isEmpty(propertyValue);
      return isEmpty ?
        DEFAULT_ELASTICSEARCH_CLIENT_TYPE :
        getElasticsearchClientType(propertyValue);
    }

    private ElasticsearchClientType getElasticsearchClientType(String propertyValue){
      boolean isExistingValue =
        Arrays
          .stream(values())
          .anyMatch(clientType -> clientType.toString().equalsIgnoreCase(propertyValue));
      return isExistingValue ? valueOf(propertyValue.toUpperCase()) : UNKNOWN;
    }
  }
}
