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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.submarine.hadoop.HdfsClient;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class HdfsClientTest {
  private static Logger LOGGER = LoggerFactory.getLogger(HdfsClientTest.class);

  private static HdfsClient hdfsClient = null;

  @BeforeClass
  public static void initEnv() {
    ZeppelinConfiguration zconf = ZeppelinConfiguration.create();
    Properties properties = new Properties();
    hdfsClient = new HdfsClient(properties);
  }

  @Test
  public void testParseText0() throws IOException {
    String text = "abc";
    String script = hdfsClient.parseText(text);
    LOGGER.info(script);
    assertEquals(script, "abc");
  }

  @Test
  public void testParseText1() throws IOException {
    String text = "%submarine abc";
    String script = hdfsClient.parseText(text);
    LOGGER.info(script);
    assertEquals(script, "abc");
  }

  @Test
  public void testParseText2() throws IOException {
    String text = "%submarine.sh abc";
    String script = hdfsClient.parseText(text);
    LOGGER.info(script);
    assertEquals(script, "abc");
  }

  @Test
  public void testParseText3() throws IOException {
    String text = "%submarine.sh(k1=v1,k2=v2) abc";
    String script = hdfsClient.parseText(text);
    LOGGER.info(script);
    assertEquals(script, "abc");
  }

  @Test
  public void testParseText4() throws IOException {
    String text = "%submarine.sh(k1=v1,k2=v2) abc";
    String script = hdfsClient.parseText(text);
    LOGGER.info(script);
    assertEquals(script, "abc");
  }

  @Test
  public void testParseText5() throws IOException {
    String text = "";
    String script = hdfsClient.parseText(text);
    LOGGER.info(script);
    assertEquals(script, "");
  }
}
