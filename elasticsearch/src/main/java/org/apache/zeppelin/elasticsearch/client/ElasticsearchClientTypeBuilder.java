/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
