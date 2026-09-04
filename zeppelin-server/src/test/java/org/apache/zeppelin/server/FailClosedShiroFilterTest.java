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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.shiro.config.Ini;
import org.apache.shiro.web.filter.mgt.DefaultFilterChainManager;
import org.apache.shiro.web.filter.mgt.FilterChainResolver;
import org.apache.shiro.web.filter.mgt.NamedFilterList;
import org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver;
import org.junit.jupiter.api.Test;

class FailClosedShiroFilterTest {

  @Test
  void removingImplicitDefaultChainPreservesExplicitFirstMatchOrder() {
    Ini ini = new Ini();
    Ini.Section urls = ini.addSection("urls");
    urls.put("/api/**", "authc");
    urls.put("/api/version", "anon");
    PathMatchingFilterChainResolver resolver = new PathMatchingFilterChainResolver();
    DefaultFilterChainManager manager =
        (DefaultFilterChainManager) resolver.getFilterChainManager();
    manager.setGlobalFilters(List.of("invalidRequest"));
    urls.forEach(manager::createChain);
    manager.createDefaultChain("/**");

    FailClosedShiroFilter.removeImplicitDefaultChain(ini, resolver);

    assertEquals(List.of("/api/**", "/api/version"), List.copyOf(manager.getChainNames()));
  }

  @Test
  void removingImplicitDefaultChainPreservesGlobalFiltersOnExplicitChains() {
    Ini ini = new Ini();
    ini.addSection("urls").put("/ws", "anon");
    PathMatchingFilterChainResolver resolver = new PathMatchingFilterChainResolver();
    DefaultFilterChainManager manager =
        (DefaultFilterChainManager) resolver.getFilterChainManager();
    manager.setGlobalFilters(List.of("invalidRequest"));
    manager.createChain("/ws", "anon");
    manager.createDefaultChain("/**");
    NamedFilterList explicitChain = manager.getFilterChains().get("/ws");

    FailClosedShiroFilter.removeImplicitDefaultChain(ini, resolver);

    assertSame(explicitChain, manager.getFilterChains().get("/ws"));
    assertEquals(2, explicitChain.size());
    assertSame(manager.getFilter("invalidRequest"), explicitChain.get(0));
    assertSame(manager.getFilter("anon"), explicitChain.get(1));
  }

  @Test
  void explicitlyConfiguredCatchAllChainIsPreserved() {
    Ini ini = new Ini();
    ini.addSection("urls").put("/**", "authc");
    PathMatchingFilterChainResolver resolver = new PathMatchingFilterChainResolver();
    DefaultFilterChainManager manager =
        (DefaultFilterChainManager) resolver.getFilterChainManager();
    manager.createChain("/**", "authc");

    FailClosedShiroFilter.removeImplicitDefaultChain(ini, resolver);

    assertTrue(manager.getChainNames().contains("/**"));
  }

  @Test
  void explicitAnonymousUrlChainContinuesRequest() throws IOException, ServletException {
    FailClosedShiroFilter filter = new FailClosedShiroFilter();
    PathMatchingFilterChainResolver resolver = new PathMatchingFilterChainResolver();
    resolver.getFilterChainManager().createChain("/ws", "anon");
    FilterChain originalChain = mock(FilterChain.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    filter.setFilterChainResolver(resolver);
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/ws");
    when(request.getServletPath()).thenReturn("/ws");

    FilterChain anonymousChain = filter.getExecutionChain(request, response, originalChain);
    anonymousChain.doFilter(request, response);

    verify(originalChain).doFilter(request, response);
    verify(response, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
  }

  @Test
  void missingUrlChainIsRejected() throws IOException, ServletException {
    FailClosedShiroFilter filter = new FailClosedShiroFilter();
    FilterChainResolver resolver = mock(FilterChainResolver.class);
    FilterChain originalChain = mock(FilterChain.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    filter.setFilterChainResolver(resolver);
    when(resolver.getChain(request, response, originalChain)).thenReturn(null);

    FilterChain deniedChain = filter.getExecutionChain(request, response, originalChain);
    deniedChain.doFilter(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    verify(originalChain, never()).doFilter(request, response);
  }

  @Test
  void missingResolverIsRejected() throws IOException, ServletException {
    FailClosedShiroFilter filter = new FailClosedShiroFilter();
    FilterChain originalChain = mock(FilterChain.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    FilterChain deniedChain = filter.getExecutionChain(request, response, originalChain);
    deniedChain.doFilter(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    verify(originalChain, never()).doFilter(request, response);
  }
}
