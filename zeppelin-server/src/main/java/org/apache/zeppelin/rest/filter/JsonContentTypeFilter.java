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

import java.util.Locale;
import java.util.Set;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.apache.zeppelin.utils.HttpMethods;

/**
 * Restricts the request body media types accepted by REST endpoints to a small allow-list.
 * Requests carrying state-changing methods (POST/PUT/DELETE/PATCH) with a body must use
 * {@code application/json}, {@code application/x-www-form-urlencoded}, or
 * {@code multipart/form-data}; anything else is rejected with 415.
 */
@Provider
public class JsonContentTypeFilter implements ContainerRequestFilter {

  private static final Set<String> ALLOWED_TYPES = Set.of(
      "application/json",
      "application/x-www-form-urlencoded",
      "multipart/form-data");

  @Override
  public void filter(ContainerRequestContext ctx) {
    String method = ctx.getMethod();
    if (method == null || !HttpMethods.STATE_CHANGING.contains(method.toUpperCase(Locale.ROOT))) {
      return;
    }
    if (!ctx.hasEntity()) {
      return;
    }
    MediaType mt = ctx.getMediaType();
    if (mt == null || !ALLOWED_TYPES.contains(baseType(mt))) {
      ctx.abortWith(
          Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
              .entity("Unsupported Content-Type")
              .build());
    }
  }

  private static String baseType(MediaType mt) {
    return (mt.getType() + "/" + mt.getSubtype()).toLowerCase(Locale.ROOT);
  }
}
