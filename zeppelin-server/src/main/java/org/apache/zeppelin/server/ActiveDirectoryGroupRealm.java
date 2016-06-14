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

import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.ldap.AbstractLdapRealm;
import org.apache.shiro.realm.ldap.DefaultLdapContextFactory;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import java.util.*;


/**
 * Created for org.apache.zeppelin.server on 09/06/16.
 */
public class ActiveDirectoryGroupRealm extends AbstractLdapRealm {
  private static final Logger log = LoggerFactory.getLogger(ActiveDirectoryGroupRealm.class);
  private static final String ROLE_NAMES_DELIMETER = ",";
  private Map<String, String> groupRolesMap;
  private LdapContextFactory ldapContextFactory = null;

  public ActiveDirectoryGroupRealm() {
  }

  public void setLdapContextFactory(LdapContextFactory ldapContextFactory) {
    this.ldapContextFactory = ldapContextFactory;
  }

  public LdapContextFactory ensureContextFactory() {
    if (this.ldapContextFactory == null) {
      if (log.isDebugEnabled()) {
        log.debug("No LdapContextFactory specified - creating a default instance.");
      }

      DefaultLdapContextFactory defaultFactory = new DefaultLdapContextFactory();
      defaultFactory.setPrincipalSuffix(this.principalSuffix);
      defaultFactory.setSearchBase(this.searchBase);
      defaultFactory.setUrl(this.url);
      defaultFactory.setSystemUsername(this.systemUsername);
      defaultFactory.setSystemPassword(this.systemPassword);
      this.ldapContextFactory = defaultFactory;
    }

    return this.ldapContextFactory;
  }

  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    try {
      AuthenticationInfo info = this.queryForAuthenticationInfo(token, this.ensureContextFactory());
      return info;
    } catch (javax.naming.AuthenticationException var5) {
      throw new AuthenticationException("LDAP authentication failed.", var5);
    } catch (NamingException var6) {
      String msg = "LDAP naming error while attempting to authenticate user.";
      throw new AuthenticationException(msg, var6);
    }
  }

  protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    try {
      AuthorizationInfo info = this.queryForAuthorizationInfo(principals,
          this.ensureContextFactory());
      return info;
    } catch (NamingException var5) {
      String msg = "LDAP naming error while attempting to retrieve authorization for user ["
          + principals + "].";
      throw new AuthorizationException(msg, var5);
    }
  }

  public void setGroupRolesMap(Map<String, String> groupRolesMap) {
    this.groupRolesMap = groupRolesMap;
  }

  protected AuthenticationInfo queryForAuthenticationInfo(AuthenticationToken token,
                                                          LdapContextFactory ldapContextFactory)
      throws NamingException {
    UsernamePasswordToken upToken = (UsernamePasswordToken) token;
    LdapContext ctx = null;

    try {
      String userPrincipalName = upToken.getUsername();
      if (this.principalSuffix != null) {
        userPrincipalName = upToken.getUsername() + this.principalSuffix;
      }
      ctx = ldapContextFactory.getLdapContext(
          userPrincipalName, upToken.getPassword());
    } finally {
      LdapUtils.closeContext(ctx);
    }

    return this.buildAuthenticationInfo(upToken.getUsername(), upToken.getPassword());
  }

  protected AuthenticationInfo buildAuthenticationInfo(String username, char[] password) {
    return new SimpleAuthenticationInfo(username, password, this.getName());
  }

  protected AuthorizationInfo queryForAuthorizationInfo(PrincipalCollection principals,
                                                        LdapContextFactory ldapContextFactory)
      throws NamingException {
    String username = (String) this.getAvailablePrincipal(principals);
    LdapContext ldapContext = ldapContextFactory.getSystemLdapContext();

    Set roleNames;
    try {
      roleNames = this.getRoleNamesForUser(username, ldapContext);
    } finally {
      LdapUtils.closeContext(ldapContext);
    }

    return this.buildAuthorizationInfo(roleNames);
  }

  protected AuthorizationInfo buildAuthorizationInfo(Set<String> roleNames) {
    return new SimpleAuthorizationInfo(roleNames);
  }

  public Set<String> getRoleNamesForUser(String username, LdapContext ldapContext)
      throws NamingException {
    LinkedHashSet roleNames = new LinkedHashSet();
    SearchControls searchCtls = new SearchControls();
    searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    String userPrincipalName = username;
    if (this.principalSuffix != null) {
      userPrincipalName = username + this.principalSuffix;
    }

    String searchFilter = "(&(objectClass=*)(userPrincipalName=*))";
    Object[] searchArguments = new Object[]{userPrincipalName};
    NamingEnumeration answer = ldapContext.search(
        this.searchBase,
        searchFilter,
        searchArguments,
        searchCtls);

    while (true) {
      Attributes attrs;
      do {
        if (!answer.hasMoreElements()) {
          return roleNames;
        }

        SearchResult sr = (SearchResult) answer.next();
        if (log.isDebugEnabled()) {
          log.debug("Retrieving group names for user [" + sr.getName() + "]");
        }

        attrs = sr.getAttributes();
      } while (attrs == null);

      NamingEnumeration ae = attrs.getAll();

      while (ae.hasMore()) {
        Attribute attr = (Attribute) ae.next();
        if (attr.getID().equals("memberOf")) {
          Collection groupNames = LdapUtils.getAllAttributeValues(attr);
          if (log.isDebugEnabled()) {
            log.debug("Groups found for user [" + username + "]: " + groupNames);
          }

          Collection rolesForGroups = this.getRoleNamesForGroups(groupNames);
          roleNames.addAll(rolesForGroups);
        }
      }
    }
  }

  protected Collection<String> getRoleNamesForGroups(Collection<String> groupNames) {
    HashSet roleNames = new HashSet(groupNames.size());
    if (this.groupRolesMap != null) {
      Iterator i$ = groupNames.iterator();

      while (true) {
        String groupName;
        String strRoleNames;
        do {
          if (!i$.hasNext()) {
            return roleNames;
          }

          groupName = (String) i$.next();
          strRoleNames = (String) this.groupRolesMap.get(groupName);
        } while (strRoleNames == null);

        String[] arr$ = strRoleNames.split(",");
        int len$ = arr$.length;

        for (int i$1 = 0; i$1 < len$; ++i$1) {
          String roleName = arr$[i$1];
          if (log.isDebugEnabled()) {
            log.debug("User is member of group [" +
                groupName + "] so adding role [" + roleName + "]");
          }

          roleNames.add(roleName);
        }
      }
    } else {
      return roleNames;
    }
  }
}
