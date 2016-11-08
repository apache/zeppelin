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

package org.apache.zeppelin.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.apache.shiro.realm.ldap.JndiLdapContextFactory;
import org.apache.shiro.realm.ldap.JndiLdapRealm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.util.JdbcUtils;
import org.apache.zeppelin.realm.ActiveDirectoryGroupRealm;
import org.apache.zeppelin.realm.LdapRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This is class which help fetching users from different realms.
 * getUserList() function is overloaded and according to the realm passed to the function it
 * extracts users from its respective realm
 */
public class GetUserList {

  private static final Logger LOG = LoggerFactory.getLogger(GetUserList.class);

  /**
   * function to extract users from shiro.ini
   */
  public List<String> getUserList(IniRealm r) {
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


  /***
   * Get user roles from shiro.ini
   * @param r
   * @return
   */
  public List<String> getRolesList(IniRealm r) {
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

  /**
   * function to extract users from LDAP
   */
  public List<String> getUserList(JndiLdapRealm r, String searchText) {
    List<String> userList = new ArrayList<>();
    LOG.info("SearchText: " + searchText);
    String userDnTemplate = r.getUserDnTemplate();
    LOG.info("Final userDnTemplate: " + userDnTemplate);
    String userDn[] = userDnTemplate.split(",", 2);
    LOG.info("Final userDn[]: " + userDn);
    String userDnPrefix = userDn[0].split("=")[0];
    LOG.info("Final userDnPrefix: " + userDnPrefix);
    String userDnSuffix = userDn[1];
    LOG.info("Final userDnSuffix: " + userDnSuffix);
    JndiLdapContextFactory CF = (JndiLdapContextFactory) r.getContextFactory();
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
          LOG.info("CurrentUser: " + currentUser);
          userList.add(currentUser.split(":")[1].trim());
        }
      }
    } catch (Exception e) {
      LOG.error("Error retrieving User list from Ldap Realm", e);
    }
    LOG.info("UserList: " + userList);
    return userList;
  }
  
  /**
   * function to extract users from LDAP
   */
  public List<String> getUserList(LdapRealm r, String searchText) {
    List<String> userList = new ArrayList<>();
    LOG.info("SearchText: " + searchText);
    String userAttribute = r.getUserSearchAttributeName();
    String userSearchRealm = r.getUserSearchBase();
    JndiLdapContextFactory CF = (JndiLdapContextFactory) r.getContextFactory();
    try {
      LdapContext ctx = CF.getSystemLdapContext();
      SearchControls constraints = new SearchControls();
      constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
      String[] attrIDs = {userAttribute};
      constraints.setReturningAttributes(attrIDs);
      NamingEnumeration result = ctx.search(userSearchRealm, "(" 
            + userAttribute + "=" + searchText + ")", constraints);
      while (result.hasMore()) {
        Attributes attrs = ((SearchResult) result.next()).getAttributes();
        if (attrs.get(userAttribute) != null) {
          String currentUser = (String) attrs.get(userAttribute).get();
          LOG.info("CurrentUser: " + currentUser);
          userList.add(currentUser.trim());
        }
      }
    } catch (Exception e) {
      LOG.error("Error retrieving User list from Ldap Realm", e);
    }
    LOG.info("UserList: " + userList);
    return userList;
  }
  
  /***
   * Get user roles from shiro.ini
   * @param r
   * @return
   */
  public List<String> getRolesList(LdapRealm r) {
    return r.getListRoles();
  }
  

  public List<String> getUserList(ActiveDirectoryGroupRealm r, String searchText) {
    List<String> userList = new ArrayList<>();
    try {
      LdapContext ctx = r.getLdapContextFactory().getSystemLdapContext();
      userList = r.searchForUserName(searchText, ctx);
    } catch (Exception e) {
      LOG.error("Error retrieving User list from ActiveDirectory Realm", e);
    }
    return userList;
  }

  /**
   * function to extract users from JDBCs
   */
  public List<String> getUserList(JdbcRealm obj) {
    List<String> userlist = new ArrayList<>();
    PreparedStatement ps = null;
    ResultSet rs = null;
    DataSource dataSource = null;
    String authQuery = "";
    String retval[];
    String tablename = "";
    String username = "";
    String userquery = "";
    try {
      dataSource = (DataSource) FieldUtils.readField(obj, "dataSource", true);
      authQuery = (String) FieldUtils.readField(obj, "DEFAULT_AUTHENTICATION_QUERY", true);
      LOG.info(authQuery);
      String authQueryLowerCase = authQuery.toLowerCase();
      retval = authQueryLowerCase.split("from", 2);
      if (retval.length >= 2) {
        retval = retval[1].split("with|where", 2);
        tablename = retval[0];
        retval = retval[1].split("where", 2);
        if (retval.length >= 2)
          retval = retval[1].split("=", 2);
        else
          retval = retval[0].split("=", 2);
        username = retval[0];
      }

      if (StringUtils.isBlank(username) || StringUtils.isBlank(tablename)) {
        return userlist;
      }

      userquery = "select " + username + " from " + tablename;

    } catch (IllegalAccessException e) {
      LOG.error("Error while accessing dataSource for JDBC Realm", e);
      return null;
    }

    try {
      Connection con = dataSource.getConnection();
      ps = con.prepareStatement(userquery);
      rs = ps.executeQuery();
      while (rs.next()) {
        userlist.add(rs.getString(1).trim());
      }
    } catch (Exception e) {
      LOG.error("Error retrieving User list from JDBC Realm", e);
    } finally {
      JdbcUtils.closeResultSet(rs);
      JdbcUtils.closeStatement(ps);
    }
    return userlist;
  }

}
