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
package org.apache.zeppelin.rest.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class JsonContentTypeFilterTest {

  private final JsonContentTypeFilter filter = new JsonContentTypeFilter();

  @Test
  void getRequestPasses() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getMethod()).thenReturn("GET");

    filter.filter(ctx);

    verify(ctx, never()).abortWith(any());
  }

  @Test
  void postWithoutBodyPasses() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getMethod()).thenReturn("POST");
    when(ctx.hasEntity()).thenReturn(false);

    filter.filter(ctx);

    verify(ctx, never()).abortWith(any());
  }

  @Test
  void postJsonPasses() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getMethod()).thenReturn("POST");
    when(ctx.hasEntity()).thenReturn(true);
    when(ctx.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);

    filter.filter(ctx);

    verify(ctx, never()).abortWith(any());
  }

  @Test
  void postFormUrlEncodedPasses() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getMethod()).thenReturn("POST");
    when(ctx.hasEntity()).thenReturn(true);
    when(ctx.getMediaType()).thenReturn(MediaType.APPLICATION_FORM_URLENCODED_TYPE);

    filter.filter(ctx);

    verify(ctx, never()).abortWith(any());
  }

  @Test
  void postMultipartFormDataPasses() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getMethod()).thenReturn("POST");
    when(ctx.hasEntity()).thenReturn(true);
    when(ctx.getMediaType()).thenReturn(MediaType.MULTIPART_FORM_DATA_TYPE);

    filter.filter(ctx);

    verify(ctx, never()).abortWith(any());
  }

  @Test
  void postTextPlainRejected() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getMethod()).thenReturn("POST");
    when(ctx.hasEntity()).thenReturn(true);
    when(ctx.getMediaType()).thenReturn(MediaType.TEXT_PLAIN_TYPE);

    filter.filter(ctx);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    verify(ctx, times(1)).abortWith(captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals(
        Response.Status.UNSUPPORTED_MEDIA_TYPE.getStatusCode(),
        captor.getValue().getStatus());
  }

  @Test
  void postWithoutContentTypeRejected() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getMethod()).thenReturn("POST");
    when(ctx.hasEntity()).thenReturn(true);
    when(ctx.getMediaType()).thenReturn(null);

    filter.filter(ctx);

    verify(ctx, times(1)).abortWith(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"PUT", "DELETE", "PATCH"})
  void stateChangingTextPlainRejected(String method) {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getMethod()).thenReturn(method);
    when(ctx.hasEntity()).thenReturn(true);
    when(ctx.getMediaType()).thenReturn(MediaType.TEXT_PLAIN_TYPE);

    filter.filter(ctx);

    verify(ctx, times(1)).abortWith(any());
  }

  @Test
  void contentTypeWithCharsetParameterAllowed() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    when(ctx.getMethod()).thenReturn("POST");
    when(ctx.hasEntity()).thenReturn(true);
    when(ctx.getMediaType()).thenReturn(
        MediaType.valueOf("application/json; charset=UTF-8"));

    filter.filter(ctx);

    verify(ctx, never()).abortWith(any());
  }
}
