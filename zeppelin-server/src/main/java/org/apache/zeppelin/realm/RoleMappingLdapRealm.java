/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.zeppelin.realm;

import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.ldap.DefaultLdapRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RoleMappingLdapRealm is a simple LDAP realm that returns configured role for specific users.
 * Multiple roles(quoted string, comma separated) can be specified for a user.
 * Example configurations:
 * ldapRealm = org.apache.zeppelin.realm.RoleMappingLdapRealm
 * ldapRealm.rolesByUsername = "user1":"admin","user2":"role1,role2","user3":"role3"
 * ldapRealm.defaultRole = "role1,role2"
 */
public class RoleMappingLdapRealm extends DefaultLdapRealm {

  private static final Logger LOGGER = LoggerFactory.getLogger(RoleMappingLdapRealm.class);

  private static final String ROLE_NAMES_DELIMITER = ",";
  private String defaultRole = "role1";

  private final Map<String, String> rolesByUsername = new LinkedHashMap<>();


  public String getDefaultRole() {
    return defaultRole;
  }

  public void setDefaultRole(String defaultRole) {
    this.defaultRole = defaultRole;
  }

  public Map<String, String> getRolesByUsername() {
    return rolesByUsername;
  }

  public void setRolesByUsername(Map<String, String> rolesByUsername) {
    this.rolesByUsername.putAll(rolesByUsername);
  }

  @Override
  public AuthorizationInfo queryForAuthorizationInfo(PrincipalCollection principals,
                                                     LdapContextFactory ldapContextFactory) throws NamingException {
    String username = (String) getAvailablePrincipal(principals);
    LdapContext ldapContext = ldapContextFactory.getSystemLdapContext();
    Set<String> roleNames = getRoleNamesForUser(username, ldapContext, getUserDnTemplate());
    return new SimpleAuthorizationInfo(roleNames);
  }

  public Set<String> getRoleNamesForUser(String username, LdapContext ldapContext,
                                         String userDnTemplate) {
    String roleString = rolesByUsername.getOrDefault(username, defaultRole);

    Set<String> roleNames = Arrays.stream(roleString.split(ROLE_NAMES_DELIMITER))
        .map(String::trim).collect(Collectors.toSet());

    LOGGER.debug("For user {}, RoleMappingLdapRealm returns {} as the role", username, roleNames);
    return roleNames;
  }
}
