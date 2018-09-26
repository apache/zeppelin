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

package org.apache.zeppelin.realm;

import java.util.List;

import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;

import org.apache.shiro.authc.AuthenticationToken;

import static java.util.Objects.requireNonNull;

/**
 * Parser for the unsanitized input data from the client WWW-Authenticate header. See RFC 4559.
 */
public class AuthorizeHeaderToken implements AuthenticationToken {
  private final byte[] authorizeHeaderValue;

  private static final Splitter SPLITTER = Splitter.on(" ");
  private static final BaseEncoding BASE64 = BaseEncoding.base64();

  public AuthorizeHeaderToken(String authorizeHeaderValue) throws IllegalArgumentException {
    requireNonNull(authorizeHeaderValue);
    List<String> parts = SPLITTER.splitToList(authorizeHeaderValue);
    if (parts.size() != 2 || !"Negotiate".equals(parts.get(0))) {
      throw new IllegalArgumentException("Malformed Authorize header: " + authorizeHeaderValue);
    }

    this.authorizeHeaderValue = BASE64.decode(parts.get(1));
  }

  @Override
  public Object getPrincipal() {
    // We don't know the principal that we're attempting to authenticate as until we've actually
    // succeeded - this data is encapsulated in the credentials we pass to GssManager.
    return null;
  }

  /**
   * Required by the API, but in-package consumers should use the type-safe
   * {@link #getAuthorizeHeaderValue} method instead.
   */
  @Override
  public Object getCredentials() {
    return getAuthorizeHeaderValue();
  }

  /**
   * The decoded value of the {@code Authorize} header.
   */
  byte[] getAuthorizeHeaderValue() {
    return authorizeHeaderValue;
  }
}
