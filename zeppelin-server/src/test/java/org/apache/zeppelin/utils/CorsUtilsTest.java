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
package org.apache.zeppelin.utils;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.jupiter.api.Test;

class CorsUtilsTest {

  @Test
  void isInvalid() throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin("http://127.0.1.1", localOriginConfiguration()));
  }

  @Test
  void isInvalidFromConfig()
      throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin("http://otherinvalidhost.com",
        ZeppelinConfiguration.load("test-zeppelin-site2.xml")));
  }

  @Test
  void isLocalhost() throws URISyntaxException, UnknownHostException {
    assertTrue(CorsUtils.isValidOrigin("http://localhost:8080", localOriginConfiguration()));
    assertTrue(CorsUtils.isValidOrigin("http://[::1]:8080", localOriginConfiguration()));
  }

  @Test
  void isLocalMachine() throws URISyntaxException, UnknownHostException {
    String origin = "http://" + InetAddress.getLocalHost().getHostName() + ":8080";
    assertTrue(CorsUtils.isValidOrigin(origin, localOriginConfiguration()),
      "Origin " + origin + " is not allowed. Please check your hostname.");
  }

  @Test
  void isValidFromConfig()
      throws URISyntaxException, UnknownHostException {
    assertTrue(CorsUtils.isValidOrigin("http://otherhost.com",
        ZeppelinConfiguration.load("test-zeppelin-site2.xml")));
  }

  @Test
  void configuredAllowlistDoesNotImplicitlyIncludeLocalhost()
      throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin("http://localhost:8080",
        ZeppelinConfiguration.load("test-zeppelin-site2.xml")));
  }

  @Test
  void isValidFromStar()
      throws URISyntaxException, UnknownHostException {
    assertTrue(CorsUtils.isValidOrigin("http://anyhost.com",
        ZeppelinConfiguration.load("zeppelin-site-star.xml")));
  }

  @Test
  void nullOrigin()
      throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin(null,
        ZeppelinConfiguration.load("zeppelin-site.xml")));
  }

  @Test
  void nullOriginWithStar()
      throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin(null,
        ZeppelinConfiguration.load("zeppelin-site-star.xml")));
  }

  @Test
  void emptyOrigin()
      throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin("",
        ZeppelinConfiguration.load("zeppelin-site.xml")));
  }

  @Test
  void notAURIOrigin()
      throws UnknownHostException {
    assertThrows(URISyntaxException.class, () -> CorsUtils.isValidOrigin(
        "test123", ZeppelinConfiguration.load("zeppelin-site.xml")));
  }

  @Test
  void localOriginMustMatchTheConfiguredSchemeAndPort()
      throws URISyntaxException, UnknownHostException {
    ZeppelinConfiguration zConf = localOriginConfiguration();

    assertFalse(CorsUtils.isValidOrigin("http://localhost:8081", zConf));
    assertFalse(CorsUtils.isValidOrigin("https://localhost:8080", zConf));
  }

  @Test
  void rejectsOriginComponentsOutsideTheOriginTuple() {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load("zeppelin-site-star.xml");

    assertThrows(URISyntaxException.class,
        () -> CorsUtils.isValidOrigin("http://localhost:8080/path", zConf));
    assertThrows(URISyntaxException.class,
        () -> CorsUtils.isValidOrigin("http://user@localhost:8080", zConf));
    assertThrows(URISyntaxException.class,
        () -> CorsUtils.isValidOrigin("http://localhost:8080?query", zConf));
  }

  private static ZeppelinConfiguration localOriginConfiguration() {
    return ZeppelinConfiguration.load("zeppelin-site.xml");
  }
}
