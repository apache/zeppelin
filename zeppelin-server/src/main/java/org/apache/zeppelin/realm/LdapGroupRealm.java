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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.ldap.JndiLdapRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.shiro.realm.ldap.JndiLdapContextFactory;


/**
 * Created for org.apache.zeppelin.server on 09/06/16.
 */
public class LdapGroupRealm extends JndiLdapRealm implements UserLookup {
    
  private static final Logger LOG = LoggerFactory.getLogger(LdapGroupRealm.class);

  public AuthorizationInfo queryForAuthorizationInfo(
      PrincipalCollection principals,
      LdapContextFactory ldapContextFactory) throws NamingException {
    String username = (String) getAvailablePrincipal(principals);
    LdapContext ldapContext = ldapContextFactory.getSystemLdapContext();
    Set<String> roleNames = getRoleNamesForUser(username, ldapContext, getUserDnTemplate());
    return new SimpleAuthorizationInfo(roleNames);
  }


  public Set<String> getRoleNamesForUser(String username,
                                         LdapContext ldapContext,
                                         String userDnTemplate) throws NamingException {
    try {
      Set<String> roleNames = new LinkedHashSet<>();

      SearchControls searchCtls = new SearchControls();
      searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

      String searchFilter = "(&(objectClass=groupOfNames)(member=" + userDnTemplate + "))";
      Object[] searchArguments = new Object[]{username};

      NamingEnumeration<?> answer = ldapContext.search(
          String.valueOf(ldapContext.getEnvironment().get("ldap.searchBase")),
          searchFilter,
          searchArguments,
          searchCtls);

      while (answer.hasMoreElements()) {
        SearchResult sr = (SearchResult) answer.next();
        Attributes attrs = sr.getAttributes();
        if (attrs != null) {
          NamingEnumeration<?> ae = attrs.getAll();
          while (ae.hasMore()) {
            Attribute attr = (Attribute) ae.next();
            if (attr.getID().equals("cn")) {
              roleNames.add((String) attr.get());
            }
          }
        }
      }
      return roleNames;

    } catch (Exception e) {
      LOG.error("Error", e);
    }

    return new HashSet<>();
  }
  
  /**
   * function to extract users from LDAP
   */
  @Override
  public List<String> lookupUsers(String searchText) {
    List<String> userList = new ArrayList<>();
    String userDnTemplate = getUserDnTemplate();
    String userDn[] = userDnTemplate.split(",", 2);
    String userDnPrefix = userDn[0].split("=")[0];
    String userDnSuffix = userDn[1];
    JndiLdapContextFactory CF = (JndiLdapContextFactory) getContextFactory();
    try {
      LdapContext ctx = CF.getSystemLdapContext();
      SearchControls constraints = new SearchControls();
      constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
      String[] attrIDs = {userDnPrefix};
      constraints.setReturningAttributes(attrIDs);
      NamingEnumeration result = ctx.search(userDnSuffix, "(" + userDnPrefix + "=*" + searchText +
          "*)", constraints);
      while (result.hasMore()) {
        Attributes attrs = ((SearchResult) result.next()).getAttributes();
        if (attrs.get(userDnPrefix) != null) {
          String currentUser = attrs.get(userDnPrefix).toString();
          userList.add(currentUser.split(":")[1].trim());
        }
      }
    } catch (Exception e) {
      LOG.error("Error retrieving User list from Ldap Realm", e);
    }
    LOG.info("UserList: " + userList);
    return userList;
  }

  @Override
  public Collection<String> lookupRoles(String query) {
    return Collections.EMPTY_SET;
  }
  
  
}
