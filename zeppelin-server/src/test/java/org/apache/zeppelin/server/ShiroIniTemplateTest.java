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
package org.apache.zeppelin.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.shiro.config.Ini;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.web.config.WebIniSecurityManagerFactory;
import org.junit.jupiter.api.Test;

class ShiroIniTemplateTest {

  @Test
  void templateUsesUnifiedUrlPolicyAndLoadsWithCurrentShiro() {
    Path template = findTemplate("shiro.ini.template");
    Ini ini = Ini.fromResourcePath(template.toUri().toString());
    Ini.Section urls = ini.getSection("urls");
    assertEquals("authc", urls.get("/ws"));
    assertEquals("authc", urls.get("/**"));
    List<String> orderedPaths = List.copyOf(urls.keySet());
    assertTrue(orderedPaths.indexOf("/ws") < orderedPaths.indexOf("/**"));

    WebIniSecurityManagerFactory factory = new WebIniSecurityManagerFactory(ini);
    try {
      SecurityManager securityManager = factory.getInstance();
      assertTrue(securityManager != null);
    } finally {
      factory.destroy();
    }
  }

  @Test
  void zeppelinSiteTemplateDoesNotEnableWildcardOrigins() throws Exception {
    String template = Files.readString(findTemplate("zeppelin-site.xml.template"));
    Pattern allowedOrigins = Pattern.compile(
        "<name>zeppelin\\.server\\.allowed\\.origins</name>\\s*<value>\\s*</value>");

    assertTrue(allowedOrigins.matcher(template).find());
  }

  private static Path findTemplate(String fileName) {
    Path workingDirectory = Path.of(System.getProperty("user.dir"));
    Path template = workingDirectory.resolve("conf").resolve(fileName);
    if (!Files.exists(template)) {
      template = workingDirectory.resolve("../conf").resolve(fileName).normalize();
    }
    assertTrue(Files.exists(template), "Cannot find conf/" + fileName);
    return template;
  }
}
