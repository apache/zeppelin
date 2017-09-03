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
package org.apache.zeppelin.conf;

import org.apache.commons.configuration.ConfigurationConverter;
import org.apache.commons.configuration.ConfigurationException;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.StringReader;
import java.util.Map;

import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.assertThat;

public class HadoopLikeConfigurationTest {

  @Test
  public void testParsing() throws ConfigurationException {
    HadoopLikeConfiguration configuration = new HadoopLikeConfiguration();
    configuration.load(new StringReader(
        "<configuration>" +
            " <property>" +
            "   <name>key1</name>" +
            "   <value>val1</value>" +
            " </property>" +
            " <property>" +
            "   <name>key2</name>" +
            "   <value>val2</value>" +
            " </property>" +
            "</configuration>"
    ));
    Map<Object, Object> map = ConfigurationConverter.getMap(configuration);
    assertThat(map, Matchers.allOf(
        hasEntry((Object) "key1", (Object) "val1"),
        hasEntry((Object) "key2", (Object) "val2")
    ));
  }
}
