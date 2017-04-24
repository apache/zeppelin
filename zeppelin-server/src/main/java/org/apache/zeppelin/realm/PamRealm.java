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

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.crypto.hash.DefaultHashService;
import org.apache.shiro.crypto.hash.Hash;
import org.apache.shiro.crypto.hash.HashRequest;
import org.apache.shiro.crypto.hash.HashService;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.UnixUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An {@code AuthorizingRealm} base on libpam4j.
 */
public class PamRealm extends AuthorizingRealm {

  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinHubRealm.class);

  private String service;

  @Override
  protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    Set<String> roles = new LinkedHashSet<>();

    UserPrincipal user = principals.oneByType(UserPrincipal.class);

    if (user != null){
      roles.addAll(user.getUnixUser().getGroups());
    }

    return new SimpleAuthorizationInfo(roles);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {

    UsernamePasswordToken userToken = (UsernamePasswordToken) token;
    UnixUser user;

    try {
      user = (new PAM(this.getService()))
          .authenticate(userToken.getUsername(), new String(userToken.getPassword()));
    } catch (PAMException e) {
      throw new AuthenticationException("Authentication failed for PAM.", e);
    }

    return new SimpleAuthenticationInfo(
        new UserPrincipal(user),
        userToken.getCredentials(),
        getName());
  }

  public String getService() {
    return service;
  }

  public void setService(String service) {
    this.service = service;
  }

}
