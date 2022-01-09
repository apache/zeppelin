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

import org.junit.Test;

import static org.apache.zeppelin.elasticsearch.client.ElasticsearchClientType.HTTP;
import static org.apache.zeppelin.elasticsearch.client.ElasticsearchClientType.HTTPS;
import static org.apache.zeppelin.elasticsearch.client.ElasticsearchClientType.TRANSPORT;
import static org.apache.zeppelin.elasticsearch.client.ElasticsearchClientType.UNKNOWN;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.is;

public class ElasticsearchClientTypeBuilderTest {

  @Test
  public void it_should_return_transport_as_default_value_when_property_is_empty() {
    //GIVEN
    String empty = "";
    //WHEN
    ElasticsearchClientType clientType =
        ElasticsearchClientTypeBuilder.withPropertyValue(empty).build();
    //THEN
    assertThat(clientType, is(TRANSPORT));
  }

  @Test
  public void it_should_return_transport_as_default_value_when_property_is_null() {
    //GIVEN
    String nullValue = null;
    //WHEN
    ElasticsearchClientType clientType =
        ElasticsearchClientTypeBuilder.withPropertyValue(nullValue).build();
    //THEN
    assertThat(clientType, is(TRANSPORT));
  }

  @Test
  public void it_should_return_client_type_when_property_value_exists() {
    //GIVEN
    String clientType = "https";
    //WHEN
    ElasticsearchClientType esClientType =
        ElasticsearchClientTypeBuilder.withPropertyValue(clientType).build();
    //THEN
    assertThat(esClientType, is(HTTPS));
  }

  @Test
  public void it_should_return_client_type_and_ignore_case_when_property_value_exists() {
    //GIVEN
    String clientType = "hTtP";
    //WHEN
    ElasticsearchClientType esClientType =
        ElasticsearchClientTypeBuilder.withPropertyValue(clientType).build();
    //THEN
    assertThat(esClientType, is(HTTP));
  }

  @Test
  public void it_should_return_unknown_when_property_value_does_not_exist() {
    //GIVEN
    String unknownValue = "an_unknown_value";
    //WHEN
    ElasticsearchClientType esClientType =
        ElasticsearchClientTypeBuilder.withPropertyValue(unknownValue).build();
    //THEN
    assertThat(esClientType, is(UNKNOWN));
  }
}
