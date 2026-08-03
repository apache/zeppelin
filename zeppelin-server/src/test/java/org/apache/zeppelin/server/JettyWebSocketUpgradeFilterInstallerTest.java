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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.servlet.WebSocketUpgradeFilter;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import jakarta.servlet.DispatcherType;

class JettyWebSocketUpgradeFilterInstallerTest {

  @Test
  void shiroMappingsPrecedeTheReusableWebSocketUpgradeFilter() {
    WebAppContext webApp = new WebAppContext();
    FilterHolder shiroFilter =
        webApp.addFilter(
            ShiroFilter.class, "/api/*", EnumSet.allOf(DispatcherType.class));

    FilterHolder upgradeFilter =
        JettyWebSocketUpgradeFilterInstaller.installAfterAuthenticationFilter(
            webApp, shiroFilter);
    FilterHolder[] filters = webApp.getServletHandler().getFilters();
    FilterMapping[] mappings = webApp.getServletHandler().getFilterMappings();

    assertEquals(2, filters.length);
    assertSame(shiroFilter, filters[0]);
    assertSame(upgradeFilter, filters[1]);
    assertEquals(WebSocketUpgradeFilter.class.getName(), upgradeFilter.getName());

    assertEquals(3, mappings.length);
    assertEquals(shiroFilter.getName(), mappings[0].getFilterName());
    assertArrayEquals(new String[] {"/api/*"}, mappings[0].getPathSpecs());
    assertEquals(shiroFilter.getName(), mappings[1].getFilterName());
    assertArrayEquals(new String[] {"/ws"}, mappings[1].getPathSpecs());
    assertEquals(upgradeFilter.getName(), mappings[2].getFilterName());
    assertArrayEquals(new String[] {"/*"}, mappings[2].getPathSpecs());
    assertEquals(EnumSet.of(DispatcherType.REQUEST), mappings[2].getDispatcherTypes());
    assertTrue(upgradeFilter.isAsyncSupported());

    assertSame(
        upgradeFilter,
        WebSocketUpgradeFilter.ensureFilter(webApp.getServletContext()),
        "Jetty's WebSocket initializer must reuse the explicitly ordered filter");
    assertEquals(3, webApp.getServletHandler().getFilterMappings().length);
  }
}
