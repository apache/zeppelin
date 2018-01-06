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
package org.apache.zeppelin.realm.jwt;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * Created for org.apache.zeppelin.server
 */
public class JWTAuthenticationToken implements AuthenticationToken {

  private Object userId;
  private String token;

  public JWTAuthenticationToken(Object userId, String token) {
    this.userId = userId;
    this.token = token;
  }

  @Override
  public Object getPrincipal() {
    return getUserId();
  }

  @Override
  public Object getCredentials() {
    return getToken();
  }

  public Object getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }
}
