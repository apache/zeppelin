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
package org.apache.zeppelin.service;

import com.google.common.collect.Lists;
import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.apache.shiro.realm.ldap.JndiLdapContextFactory;
import org.apache.shiro.realm.ldap.JndiLdapRealm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.JdbcUtils;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.realm.ActiveDirectoryGroupRealm;
import org.apache.zeppelin.realm.LdapRealm;
import org.apache.zeppelin.realm.UserRoleSearchable;
import org.apache.zeppelin.server.ZeppelinServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tools for securing Zeppelin. */
public class ShiroSecurityService implements SecurityService {

  private final Logger LOGGER = LoggerFactory.getLogger(ShiroSecurityService.class);

  @Inject
  public ShiroSecurityService(ZeppelinConfiguration zeppelinConfiguration) throws Exception {
    if (zeppelinConfiguration.getShiroPath().length() > 0) {
      try {
        Collection<Realm> realms =
            ((DefaultWebSecurityManager) org.apache.shiro.SecurityUtils.getSecurityManager())
                .getRealms();
        if (realms.size() > 1) {
          Boolean isIniRealmEnabled = false;
          for (Object realm : realms) {
            if (realm instanceof IniRealm && ((IniRealm) realm).getIni().get("users") != null) {
              isIniRealmEnabled = true;
              break;
            }
          }
          if (isIniRealmEnabled) {
            throw new Exception(
                "IniRealm/password based auth mechanisms should be exclusive. "
                    + "Consider removing [users] block from shiro.ini");
          }
        }
      } catch (UnavailableSecurityManagerException e) {
        LOGGER.error("Failed to initialise shiro configuraion", e);
      }
    }
  }

  /**
   * Return the authenticated user if any otherwise returns "anonymous".
   *
   * @return shiro principal
   */
  @Override
  public String getPrincipal() {
    Subject subject = org.apache.shiro.SecurityUtils.getSubject();

    String principal;
    if (subject.isAuthenticated()) {
      principal = extractPrincipal(subject);
      if (ZeppelinServer.notebook.getConf().isUsernameForceLowerCase()) {
        LOGGER.debug(
            "Converting principal name " + principal + " to lower case:" + principal.toLowerCase());
        principal = principal.toLowerCase();
      }
    } else {
      // TODO(jl): Could be better to occur error?
      principal = "anonymous";
    }
    return principal;
  }

  private String extractPrincipal(Subject subject) {
    String principal;
    Object principalObject = subject.getPrincipal();
    if (principalObject instanceof Principal) {
      principal = ((Principal) principalObject).getName();
    } else {
      principal = String.valueOf(principalObject);
    }
    return principal;
  }

  @Override
  public Collection getRealmsList() {
    DefaultWebSecurityManager defaultWebSecurityManager;
    String key = ThreadContext.SECURITY_MANAGER_KEY;
    defaultWebSecurityManager = (DefaultWebSecurityManager) ThreadContext.get(key);
    return defaultWebSecurityManager.getRealms();
  }

  /** Checked if shiro enabled or not. */
  @Override
  public boolean isAuthenticated() {
    return org.apache.shiro.SecurityUtils.getSubject().isAuthenticated();
  }

  /**
   * Get candidated users based on searchText
   *
   * @param searchText
   * @param numUsersToFetch
   * @return
   */
  @Override
  public List<String> getMatchedUsers(String searchText, int numUsersToFetch) {
    List<String> usersList = new ArrayList<>();
    try {
      Collection<Realm> realmsList = (Collection<Realm>) getRealmsList();
      if (realmsList != null) {
        for (Realm realm : realmsList) {
          String name = realm.getClass().getName();
          LOGGER.debug("RealmClass.getName: " + name);
          if (realm instanceof UserRoleSearchable) {
            usersList.addAll(((UserRoleSearchable)realm).searchForUser(searchText, numUsersToFetch));
          } else if (name.equals("org.apache.shiro.realm.text.IniRealm")) {
            usersList.addAll(getUserList((IniRealm) realm));
          } else if (name.equals("org.apache.zeppelin.realm.LdapGroupRealm")) {
            usersList.addAll(getUserList((JndiLdapRealm) realm, searchText, numUsersToFetch));
          } else if (name.equals("org.apache.zeppelin.realm.LdapRealm")) {
            usersList.addAll(getUserList((LdapRealm) realm, searchText, numUsersToFetch));
          } else if (name.equals("org.apache.zeppelin.realm.ActiveDirectoryGroupRealm")) {
            usersList.addAll(
                getUserList((ActiveDirectoryGroupRealm) realm, searchText, numUsersToFetch));
          } else if (name.equals("org.apache.shiro.realm.jdbc.JdbcRealm")) {
            usersList.addAll(getUserList((JdbcRealm) realm));
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Exception in retrieving Users from realms ", e);
    }
    return usersList;
  }

  /**
   * Get matched roles.
   *
   * @return
   */
  @Override
  public List<String> getMatchedRoles(String searchText, int numUsersToFetch) {
    List<String> rolesList = new ArrayList<>();
    try {
      Collection realmsList = getRealmsList();
      if (realmsList != null) {
        for (Iterator<Realm> iterator = realmsList.iterator(); iterator.hasNext(); ) {
          Realm realm = iterator.next();
          String name = realm.getClass().getName();
          LOGGER.debug("RealmClass.getName: " + name);
          if (realm instanceof UserRoleSearchable) {
            rolesList.addAll(((UserRoleSearchable)realm).searchForRole(searchText, numUsersToFetch));
          } else if (name.equals("org.apache.shiro.realm.text.IniRealm")) {
            rolesList.addAll(getRolesList((IniRealm) realm));
          } else if (name.equals("org.apache.zeppelin.realm.LdapRealm")) {
            rolesList.addAll(getRolesList((LdapRealm) realm));
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Exception in retrieving Users from realms ", e);
    }
    return rolesList;
  }

  /**
   * Return the roles associated with the authenticated user if any otherwise returns empty set.
   * TODO(prasadwagle) Find correct way to get user roles (see SHIRO-492)
   *
   * @return shiro roles
   */
  @Override
  public Set<String> getAssociatedRoles() {
    Subject subject = org.apache.shiro.SecurityUtils.getSubject();
    HashSet<String> roles = new HashSet<>();
    Map allRoles = null;

    if (subject.isAuthenticated()) {
      Collection realmsList = getRealmsList();
      for (Iterator<Realm> iterator = realmsList.iterator(); iterator.hasNext(); ) {
        Realm realm = iterator.next();
        String name = realm.getClass().getName();
        if (name.equals("org.apache.shiro.realm.text.IniRealm")) {
          allRoles = ((IniRealm) realm).getIni().get("roles");
          break;
        } else if (name.equals("org.apache.zeppelin.realm.LdapRealm")) {
          try {
            AuthorizationInfo auth =
                ((LdapRealm) realm)
                    .queryForAuthorizationInfo(
                        new SimplePrincipalCollection(subject.getPrincipal(), realm.getName()),
                        ((LdapRealm) realm).getContextFactory());
            if (auth != null) {
              roles = new HashSet<>(auth.getRoles());
            }
          } catch (NamingException e) {
            LOGGER.error("Can't fetch roles", e);
          }
          break;
        } else if (name.equals("org.apache.zeppelin.realm.ActiveDirectoryGroupRealm")) {
          allRoles = ((ActiveDirectoryGroupRealm) realm).getListRoles();
          break;
        }
      }
      if (allRoles != null) {
        Iterator it = allRoles.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry pair = (Map.Entry) it.next();
          if (subject.hasRole((String) pair.getKey())) {
            roles.add((String) pair.getKey());
          }
        }
      }
    }
    return roles;
  }

  /** Function to extract users from shiro.ini. */
  private List<String> getUserList(IniRealm r) {
    List<String> userList = new ArrayList<>();
    Map getIniUser = r.getIni().get("users");
    if (getIniUser != null) {
      Iterator it = getIniUser.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry pair = (Map.Entry) it.next();
        userList.add(pair.getKey().toString().trim());
      }
    }
    return userList;
  }

  /**
   * * Get user roles from shiro.ini.
   *
   * @param r
   * @return
   */
  private List<String> getRolesList(IniRealm r) {
    List<String> roleList = new ArrayList<>();
    Map getIniRoles = r.getIni().get("roles");
    if (getIniRoles != null) {
      Iterator it = getIniRoles.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry pair = (Map.Entry) it.next();
        roleList.add(pair.getKey().toString().trim());
      }
    }
    return roleList;
  }

  /** Function to extract users from LDAP. */
  private List<String> getUserList(JndiLdapRealm r, String searchText, int numUsersToFetch) {
    List<String> userList = new ArrayList<>();
    String userDnTemplate = r.getUserDnTemplate();
    String userDn[] = userDnTemplate.split(",", 2);
    String userDnPrefix = userDn[0].split("=")[0];
    String userDnSuffix = userDn[1];
    JndiLdapContextFactory cf = (JndiLdapContextFactory) r.getContextFactory();
    try {
      LdapContext ctx = cf.getSystemLdapContext();
      SearchControls constraints = new SearchControls();
      constraints.setCountLimit(numUsersToFetch);
      constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
      String[] attrIDs = {userDnPrefix};
      constraints.setReturningAttributes(attrIDs);
      NamingEnumeration result =
          ctx.search(userDnSuffix, "(" + userDnPrefix + "=*" + searchText + "*)", constraints);
      while (result.hasMore()) {
        Attributes attrs = ((SearchResult) result.next()).getAttributes();
        if (attrs.get(userDnPrefix) != null) {
          String currentUser = attrs.get(userDnPrefix).toString();
          userList.add(currentUser.split(":")[1].trim());
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error retrieving User list from Ldap Realm", e);
    }
    LOGGER.info("UserList: " + userList);
    return userList;
  }

  /** Function to extract users from Zeppelin LdapRealm. */
  private List<String> getUserList(LdapRealm r, String searchText, int numUsersToFetch) {
    List<String> userList = new ArrayList<>();
    LOGGER.debug("SearchText: " + searchText);
    String userAttribute = r.getUserSearchAttributeName();
    String userSearchRealm = r.getUserSearchBase();
    String userObjectClass = r.getUserObjectClass();
    JndiLdapContextFactory cf = (JndiLdapContextFactory) r.getContextFactory();
    try {
      LdapContext ctx = cf.getSystemLdapContext();
      SearchControls constraints = new SearchControls();
      constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
      constraints.setCountLimit(numUsersToFetch);
      String[] attrIDs = {userAttribute};
      constraints.setReturningAttributes(attrIDs);
      NamingEnumeration result =
          ctx.search(
              userSearchRealm,
              "(&(objectclass="
                  + userObjectClass
                  + ")("
                  + userAttribute
                  + "=*"
                  + searchText
                  + "*))",
              constraints);
      while (result.hasMore()) {
        Attributes attrs = ((SearchResult) result.next()).getAttributes();
        if (attrs.get(userAttribute) != null) {
          String currentUser;
          if (r.getUserLowerCase()) {
            LOGGER.debug("userLowerCase true");
            currentUser = ((String) attrs.get(userAttribute).get()).toLowerCase();
          } else {
            LOGGER.debug("userLowerCase false");
            currentUser = (String) attrs.get(userAttribute).get();
          }
          LOGGER.debug("CurrentUser: " + currentUser);
          userList.add(currentUser.trim());
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error retrieving User list from Ldap Realm", e);
    }
    return userList;
  }

  /**
   * * Get user roles from shiro.ini for Zeppelin LdapRealm.
   *
   * @param r
   * @return
   */
  private List<String> getRolesList(LdapRealm r) {
    List<String> roleList = new ArrayList<>();
    Map<String, String> roles = r.getListRoles();
    if (roles != null) {
      Iterator it = roles.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry pair = (Map.Entry) it.next();
        LOGGER.debug("RoleKeyValue: " + pair.getKey() + " = " + pair.getValue());
        roleList.add((String) pair.getKey());
      }
    }
    return roleList;
  }

  private List<String> getUserList(
      ActiveDirectoryGroupRealm r, String searchText, int numUsersToFetch) {
    List<String> userList = new ArrayList<>();
    try {
      LdapContext ctx = r.getLdapContextFactory().getSystemLdapContext();
      userList = r.searchForUserName(searchText, ctx, numUsersToFetch);
    } catch (Exception e) {
      LOGGER.error("Error retrieving User list from ActiveDirectory Realm", e);
    }
    return userList;
  }

  /** Function to extract users from JDBCs. */
  private List<String> getUserList(JdbcRealm obj) {
    List<String> userlist = new ArrayList<>();
    Connection con = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    DataSource dataSource = null;
    String authQuery = "";
    String retval[];
    String tablename = "";
    String username = "";
    String userquery;
    try {
      dataSource = (DataSource) FieldUtils.readField(obj, "dataSource", true);
      authQuery = (String) FieldUtils.readField(obj, "authenticationQuery", true);
      LOGGER.info(authQuery);
      String authQueryLowerCase = authQuery.toLowerCase();
      retval = authQueryLowerCase.split("from", 2);
      if (retval.length >= 2) {
        retval = retval[1].split("with|where", 2);
        tablename = retval[0];
        retval = retval[1].split("where", 2);
        if (retval.length >= 2) {
          retval = retval[1].split("=", 2);
        } else {
          retval = retval[0].split("=", 2);
        }
        username = retval[0];
      }

      if (StringUtils.isBlank(username) || StringUtils.isBlank(tablename)) {
        return userlist;
      }

      userquery = String.format("SELECT %s FROM %s", username, tablename);
    } catch (IllegalAccessException e) {
      LOGGER.error("Error while accessing dataSource for JDBC Realm", e);
      return Lists.newArrayList();
    }

    try {
      con = dataSource.getConnection();
      ps = con.prepareStatement(userquery);
      rs = ps.executeQuery();
      while (rs.next()) {
        userlist.add(rs.getString(1).trim());
      }
    } catch (Exception e) {
      LOGGER.error("Error retrieving User list from JDBC Realm", e);
    } finally {
      JdbcUtils.closeResultSet(rs);
      JdbcUtils.closeStatement(ps);
      JdbcUtils.closeConnection(con);
    }
    return userlist;
  }
}
