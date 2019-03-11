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

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.submarine.hadoop.YarnClient;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class YarnClientTest {
  private static Logger LOGGER = LoggerFactory.getLogger(YarnClientTest.class);

  private static YarnClient yarnClient = null;

  @BeforeClass
  public static void initEnv() {
    ZeppelinConfiguration zconf = ZeppelinConfiguration.create();
    Properties properties = new Properties();
    yarnClient = new YarnClient(properties);
  }

  @Test
  public void testParseAppAttempts() throws IOException {
    String jsonFile = "ws-v1-cluster-apps-application_id-appattempts.json";
    URL urlJson = Resources.getResource(jsonFile);
    String jsonContent = Resources.toString(urlJson, Charsets.UTF_8);

    List<Map<String, Object>> list = yarnClient.parseAppAttempts(jsonContent);

    LOGGER.info("");
  }

  @Test
  public void testParseAppAttemptsContainers() throws IOException {
    String jsonFile = "ws-v1-cluster-apps-application_id-appattempts-appattempt_id-containers.json";
    URL urlJson = Resources.getResource(jsonFile);
    String jsonContent = Resources.toString(urlJson, Charsets.UTF_8);

    List<Map<String, Object>> list = yarnClient.parseAppAttemptsContainers(jsonContent);

    list.get(0).get(YarnClient.HOST_IP);
    list.get(0).get(YarnClient.HOST_PORT);
    list.get(0).get(YarnClient.CONTAINER_PORT);

    LOGGER.info("");
  }

  @Test
  public void testParseClusterApps() throws IOException {
    String jsonFile = "ws-v1-cluster-apps-application_id-finished.json";
    URL urlJson = Resources.getResource(jsonFile);
    String jsonContent = Resources.toString(urlJson, Charsets.UTF_8);

    Map<String, Object> list = yarnClient.parseClusterApps(jsonContent);

    LOGGER.info("");
  }
}
