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


package org.apache.zeppelin.user;


import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.common.JsonSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/***
 *
 */
public class AuthenticationInfo implements JsonSerializable {
  private static final Logger LOG = LoggerFactory.getLogger(AuthenticationInfo.class);
  private static final Gson gson = new Gson();

  String user;
  List<String> roles;
  String ticket;
  UserCredentials userCredentials;
  public static final AuthenticationInfo ANONYMOUS = new AuthenticationInfo("anonymous", null,
      "anonymous");

  public AuthenticationInfo() {}

  public AuthenticationInfo(String user) {
    this.user = user;
  }

  /***
   *
   * @param user
   * @param ticket
   */
  public AuthenticationInfo(String user, String roles, String ticket) {
    this.user = user;
    this.ticket = ticket;
    if (StringUtils.isNotBlank(roles) && roles.length() > 2) {
      this.roles = new ArrayList<>();
      for (final String role : roles.substring(1, roles.length() - 1)
          .split(",")) {
        this.roles.add(role.trim());
      }
    }
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }

  public List<String> getUsersAndRoles() {
    List<String> usersAndRoles = new ArrayList<>();
    if (roles != null) {
      usersAndRoles.addAll(roles);
    }
    if (user != null) {
      usersAndRoles.add(user);
    }

    return usersAndRoles;
  }

  public String getTicket() {
    return ticket;
  }

  public void setTicket(String ticket) {
    this.ticket = ticket;
  }

  public UserCredentials getUserCredentials() {
    return userCredentials;
  }

  public void setUserCredentials(UserCredentials userCredentials) {
    this.userCredentials = userCredentials;
  }

  public static boolean isAnonymous(AuthenticationInfo subject) {
    if (subject == null) {
      LOG.warn("Subject is null, assuming anonymous. "
          + "Not recommended to use subject as null except in tests");
      return true;
    }
    return subject.isAnonymous();
  }

  public boolean isAnonymous() {
    return ANONYMOUS.equals(this) || "anonymous".equalsIgnoreCase(this.getUser())
        || StringUtils.isEmpty(this.getUser());
  }

  @Override
  public String toJson() {
    return gson.toJson(this);
  }

  public static AuthenticationInfo fromJson(String json) {
    return gson.fromJson(json, AuthenticationInfo.class);
  }
}
