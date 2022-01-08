package org.apache.zeppelin.elasticsearch.client;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ElasticsearchClientTypeTest {

  @Test
  public void it_should_return_http_when_reducing_on_http_types() {
    //GIVEN
    List<ElasticsearchClientType> httpTypes =
        new ArrayList<>(Arrays.asList(ElasticsearchClientType.HTTP, ElasticsearchClientType.HTTPS));
    //WHEN
    Boolean httpTypesReduced = httpTypes.stream()
        .map(ElasticsearchClientType::isHttp)
        .reduce(true, (ident, elasticsearchClientType) -> ident && elasticsearchClientType);
    //THEN
    assertThat(httpTypesReduced, is(true));
  }
}
