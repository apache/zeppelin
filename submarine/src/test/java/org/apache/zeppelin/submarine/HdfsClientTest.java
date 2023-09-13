/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.submarine;

import org.apache.zeppelin.submarine.hadoop.HdfsClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Properties;

public class HdfsClientTest {
  private static Logger LOGGER = LoggerFactory.getLogger(HdfsClientTest.class);

  private static HdfsClient hdfsClient = null;

  @BeforeAll
  public static void initEnv() {
    Properties properties = new Properties();
    hdfsClient = new HdfsClient(properties);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "abc",
      "%submarine abc",
      "%submarine.sh abc",
      "%submarine.sh(k1=v1,k2=v2) abc" })
  void testParseText0(String text) throws IOException {
    String script = hdfsClient.parseText(text);
    LOGGER.info(script);
    assertEquals("abc", script);
  }

  @Test
  void testParseText5() throws IOException {
    String text = "";
    String script = hdfsClient.parseText(text);
    LOGGER.info(script);
    assertEquals("", script);
  }
}
