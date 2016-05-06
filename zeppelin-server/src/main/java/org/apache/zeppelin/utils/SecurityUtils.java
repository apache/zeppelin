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

import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Tools for securing Zeppelin
 */
public class SecurityUtils {

  public static Boolean isValidOrigin(String sourceHost, ZeppelinConfiguration conf)
      throws UnknownHostException, URISyntaxException {
    if (sourceHost == null || sourceHost.isEmpty()){
      return false;
    }
    String sourceUriHost = new URI(sourceHost).getHost();
    sourceUriHost = (sourceUriHost == null) ? "" : sourceUriHost.toLowerCase();

    sourceUriHost = sourceUriHost.toLowerCase();
    String currentHost = InetAddress.getLocalHost().getHostName().toLowerCase();

    return conf.getAllowedOrigins().contains("*") ||
            currentHost.equals(sourceUriHost) ||
            "localhost".equals(sourceUriHost) ||
            conf.getAllowedOrigins().contains(sourceHost);
  }

  /**
   * Return the authenticated user if any otherwise returns "anonymous"
   * @return shiro principal
   */
  public static String getPrincipal() {
    Subject subject = org.apache.shiro.SecurityUtils.getSubject();

    String principal;
    if (subject.isAuthenticated()) {
      principal = subject.getPrincipal().toString();
    }
    else {
      principal = "anonymous";
    }
    return principal;
  }

  /**
   * Return the roles associated with the authenticated user if any otherwise returns empty set
   * TODO(prasadwagle) Find correct way to get user roles (see SHIRO-492)
   * @return shiro roles
   */
  public static HashSet<String> getRoles() {
    Subject subject = org.apache.shiro.SecurityUtils.getSubject();
    HashSet<String> roles = new HashSet<>();

    if (subject.isAuthenticated()) {
      for (String role : Arrays.asList("role1", "role2", "role3")) {
        if (subject.hasRole(role)) {
          roles.add(role);
        }
      }
    }
    return roles;
  }

}
