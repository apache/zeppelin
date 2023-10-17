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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CorsUtilsTest {

  @AfterEach
  public void cleanup() {
    ZeppelinConfiguration.reset();
  }
  @Test
  void isInvalid() throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin("http://127.0.1.1", ZeppelinConfiguration.create()));
  }

  @Test
  void isInvalidFromConfig()
      throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin("http://otherinvalidhost.com",
        ZeppelinConfiguration.create("zeppelin-site.xml")));
  }

  @Test
  void isLocalhost() throws URISyntaxException, UnknownHostException {
    assertTrue(CorsUtils.isValidOrigin("http://localhost", ZeppelinConfiguration.create()));
  }

  @Test
  void isLocalMachine() throws URISyntaxException, UnknownHostException {
    String origin = "http://" + InetAddress.getLocalHost().getHostName();
    assertTrue(CorsUtils.isValidOrigin(origin, ZeppelinConfiguration.create()),
      "Origin " + origin + " is not allowed. Please check your hostname.");
  }

  @Test
  void isValidFromConfig()
      throws URISyntaxException, UnknownHostException {
    assertTrue(CorsUtils.isValidOrigin("http://otherhost.com",
      ZeppelinConfiguration.create("zeppelin-site.xml")));
  }

  @Test
  void isValidFromStar()
      throws URISyntaxException, UnknownHostException {
    assertTrue(CorsUtils.isValidOrigin("http://anyhost.com",
      ZeppelinConfiguration.create("zeppelin-site-star.xml")));
  }

  @Test
  void nullOrigin()
      throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin(null,
      ZeppelinConfiguration.create("zeppelin-site.xml")));
  }

  @Test
  void nullOriginWithStar()
      throws URISyntaxException, UnknownHostException {
    assertTrue(CorsUtils.isValidOrigin(null,
      ZeppelinConfiguration.create("zeppelin-site-star.xml")));
  }

  @Test
  void emptyOrigin()
      throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin("",
      ZeppelinConfiguration.create("zeppelin-site.xml")));
  }

  @Test
  void notAURIOrigin()
      throws URISyntaxException, UnknownHostException {
    assertFalse(CorsUtils.isValidOrigin("test123",
      ZeppelinConfiguration.create("zeppelin-site.xml")));
  }
}
