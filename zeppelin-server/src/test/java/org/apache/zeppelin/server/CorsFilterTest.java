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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * Basic CORS REST API tests.
 */
class CorsFilterTest {

  @Test
  void validCorsFilterTest() throws IOException, ServletException {
    CorsFilter filter = new CorsFilter(ZeppelinConfiguration.load());
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    FilterChain mockedFilterChain = mock(FilterChain.class);
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getHeader("Origin")).thenReturn("http://localhost:8080");
    when(mockRequest.getMethod()).thenReturn("Empty");
    Map<String, String> setHeaders = recordSetHeaders(mockResponse);

    filter.doFilter(mockRequest, mockResponse, mockedFilterChain);

    assertEquals("http://localhost:8080", setHeaders.get("Access-Control-Allow-Origin"));
  }

  @Test
  void invalidCorsFilterTest() throws IOException, ServletException {
    CorsFilter filter = new CorsFilter(ZeppelinConfiguration.load());
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    FilterChain mockedFilterChain = mock(FilterChain.class);
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getHeader("Origin")).thenReturn("http://evillocalhost:8080");
    when(mockRequest.getMethod()).thenReturn("Empty");
    Map<String, String> setHeaders = recordSetHeaders(mockResponse);

    filter.doFilter(mockRequest, mockResponse, mockedFilterChain);

    assertEquals("", setHeaders.get("Access-Control-Allow-Origin"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
  void crossOriginStateChangingBlocked(String method) throws IOException, ServletException {
    CorsFilter filter = new CorsFilter(ZeppelinConfiguration.load());
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    FilterChain mockedFilterChain = mock(FilterChain.class);
    when(mockRequest.getHeader("Origin")).thenReturn("http://evil.example.com");
    when(mockRequest.getMethod()).thenReturn(method);

    filter.doFilter(mockRequest, mockResponse, mockedFilterChain);

    verify(mockResponse).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    verify(mockedFilterChain, never()).doFilter(mockRequest, mockResponse);
  }

  @Test
  void crossOriginPreflightBlocked() throws IOException, ServletException {
    CorsFilter filter = new CorsFilter(ZeppelinConfiguration.load());
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    FilterChain mockedFilterChain = mock(FilterChain.class);
    when(mockRequest.getHeader("Origin")).thenReturn("http://evil.example.com");
    when(mockRequest.getHeader("Access-Control-Request-Method")).thenReturn("POST");
    when(mockRequest.getMethod()).thenReturn("OPTIONS");

    filter.doFilter(mockRequest, mockResponse, mockedFilterChain);

    verify(mockResponse).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    verify(mockedFilterChain, never()).doFilter(mockRequest, mockResponse);
  }

  @Test
  void allowedOriginPostPasses() throws IOException, ServletException {
    CorsFilter filter = new CorsFilter(ZeppelinConfiguration.load());
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    FilterChain mockedFilterChain = mock(FilterChain.class);
    when(mockRequest.getHeader("Origin")).thenReturn("http://localhost");
    when(mockRequest.getMethod()).thenReturn("POST");
    Map<String, String> setHeaders = recordSetHeaders(mockResponse);

    filter.doFilter(mockRequest, mockResponse, mockedFilterChain);

    verify(mockResponse, never()).sendError(anyInt(), anyString());
    verify(mockedFilterChain, times(1)).doFilter(mockRequest, mockResponse);
    assertEquals("http://localhost", setHeaders.get("Access-Control-Allow-Origin"));
    assertEquals("true", setHeaders.get("Access-Control-Allow-Credentials"));
  }

  @Test
  void disallowedOriginGetPasses() throws IOException, ServletException {
    CorsFilter filter = new CorsFilter(ZeppelinConfiguration.load());
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    FilterChain mockedFilterChain = mock(FilterChain.class);
    when(mockRequest.getHeader("Origin")).thenReturn("http://evil.example.com");
    when(mockRequest.getMethod()).thenReturn("GET");
    Map<String, String> setHeaders = recordSetHeaders(mockResponse);

    filter.doFilter(mockRequest, mockResponse, mockedFilterChain);

    verify(mockResponse, never()).sendError(anyInt(), anyString());
    verify(mockedFilterChain, times(1)).doFilter(mockRequest, mockResponse);
    assertEquals("", setHeaders.get("Access-Control-Allow-Origin"));
    assertNull(setHeaders.get("Access-Control-Allow-Credentials"));
  }

  @Test
  void noOriginPostPasses() throws IOException, ServletException {
    CorsFilter filter = new CorsFilter(ZeppelinConfiguration.load());
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    FilterChain mockedFilterChain = mock(FilterChain.class);
    when(mockRequest.getHeader("Origin")).thenReturn(null);
    when(mockRequest.getMethod()).thenReturn("POST");

    filter.doFilter(mockRequest, mockResponse, mockedFilterChain);

    verify(mockResponse, never()).sendError(anyInt(), anyString());
    verify(mockedFilterChain, times(1)).doFilter(mockRequest, mockResponse);
  }

  @Test
  void simpleOptionsWithoutPreflightHeaderPasses() throws IOException, ServletException {
    CorsFilter filter = new CorsFilter(ZeppelinConfiguration.load());
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    FilterChain mockedFilterChain = mock(FilterChain.class);
    when(mockRequest.getHeader("Origin")).thenReturn("http://evil.example.com");
    when(mockRequest.getHeader("Access-Control-Request-Method")).thenReturn(null);
    when(mockRequest.getMethod()).thenReturn("OPTIONS");

    filter.doFilter(mockRequest, mockResponse, mockedFilterChain);

    verify(mockResponse, never()).sendError(anyInt(), anyString());
    verify(mockedFilterChain, times(1)).doFilter(mockRequest, mockResponse);
  }

  private static Map<String, String> recordSetHeaders(HttpServletResponse response) {
    Map<String, String> recorded = new HashMap<>();
    doAnswer(invocation -> {
      recorded.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(response).setHeader(anyString(), anyString());
    return recorded;
  }
}
