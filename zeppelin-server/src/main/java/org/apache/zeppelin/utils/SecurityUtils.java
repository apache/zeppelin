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

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.Sets;
import java.lang.reflect.Method;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.zeppelin.realm.UserLookup;

/**
 * Tools for securing Zeppelin
 */
public class SecurityUtils {

  private static final String ANONYMOUS = "anonymous";
  private static final HashSet<String> EMPTY_HASHSET = Sets.newHashSet();
  private static boolean isEnabled = false;
  private static final Logger log = LoggerFactory.getLogger(SecurityUtils.class);
  private static final int MAX_MATCHES_FROM_LOOKUP = 5;
  private static final String INI_SECTION_USERS = "users";
  private static final String INI_SECTION_ROLES = "roles";
  
  
  public static void initSecurityManager(String shiroPath) {
    IniSecurityManagerFactory factory = new IniSecurityManagerFactory("file:" + shiroPath);
    SecurityManager securityManager = factory.getInstance();
    org.apache.shiro.SecurityUtils.setSecurityManager(securityManager);
    isEnabled = true;
  }

  public static Boolean isValidOrigin(String sourceHost, ZeppelinConfiguration conf)
      throws UnknownHostException, URISyntaxException {

    String sourceUriHost = "";

    if (sourceHost != null && !sourceHost.isEmpty()) {
      sourceUriHost = new URI(sourceHost).getHost();
      sourceUriHost = (sourceUriHost == null) ? "" : sourceUriHost.toLowerCase();
    }

    sourceUriHost = sourceUriHost.toLowerCase();
    String currentHost = InetAddress.getLocalHost().getHostName().toLowerCase();

    return conf.getAllowedOrigins().contains("*") ||
        currentHost.equals(sourceUriHost) ||
        "localhost".equals(sourceUriHost) ||
        conf.getAllowedOrigins().contains(sourceHost);
  }

  /**
   * Return the authenticated user if any otherwise returns "anonymous"
   *
   * @return shiro principal
   */
  public static String getPrincipal() {
    if (!isEnabled) {
      return ANONYMOUS;
    }
    Subject subject = org.apache.shiro.SecurityUtils.getSubject();

    String principal;
    if (subject.isAuthenticated()) {
      principal = subject.getPrincipal().toString();
    } else {
      principal = ANONYMOUS;
    }
    return principal;
  }

  public static Collection getRealmsList() {
    if (!isEnabled) {
      return Collections.emptyList();
    }
    DefaultWebSecurityManager defaultWebSecurityManager;
    String key = ThreadContext.SECURITY_MANAGER_KEY;
    defaultWebSecurityManager = (DefaultWebSecurityManager) ThreadContext.get(key);
    Collection<Realm> realms = defaultWebSecurityManager.getRealms();
    return realms;
  }

  /**
   * Return the roles associated with the authenticated user if any otherwise returns empty set
   * TODO(prasadwagle) Find correct way to get user roles (see SHIRO-492)
   *
   * @return shiro roles
   */
  public static HashSet<String> getRoles() {
    if (!isEnabled) {
      return EMPTY_HASHSET;
    }
    Subject subject = org.apache.shiro.SecurityUtils.getSubject();
    HashSet<String> roles = new HashSet<>();

    if (subject.isAuthenticated()) {
      PrincipalCollection principals = subject.getPrincipals();
  
      Collection realmsList = SecurityUtils.getRealmsList();
      for (Iterator<Realm> iterator = realmsList.iterator(); iterator.hasNext(); ) {
        Realm realm = iterator.next();
        if (realm instanceof AuthorizingRealm) {
          AuthorizingRealm authRealm = (AuthorizingRealm) realm;
          AuthorizationInfo info = extractAuthorizationInfoOrNull(authRealm, principals);
          if (info != null) {
            roles.addAll(info.getRoles());
          }
        }
      }
      
    }
    return roles;
  }
  
  /**
   * Uses reflection to call a protected method on the AuthorizingRealm class to 
   * work around SHIRO-492.
   * 
   * @param authRealm The realm to query 
   * @param principals The principals of the authenticated user
   * @return AuthorizationInfo of the authenticated user or null
   */
  private static AuthorizationInfo extractAuthorizationInfoOrNull(
    AuthorizingRealm authRealm, PrincipalCollection principals) {
    try {
      Method method = AuthorizingRealm.class
                      .getDeclaredMethod("getAuthorizationInfo", PrincipalCollection.class);
      method.setAccessible(true);
      return (AuthorizationInfo) method.invoke(authRealm, principals);
    } catch (Exception e) {
      log.warn("Unable to extract authorization info from realm {}", authRealm.getName(), e);
      return null;
    }
  }
  
  public static Collection<String> lookupUsersInRealms(final String searchText) {
    Set<String> userList = new TreeSet<>(new PreferStartsWithComparator(searchText));
    
    // start by adding the authenticated user's name
    userList.add(getPrincipal());
    
    // check if the configured realms support lookup
    Collection realmsList = SecurityUtils.getRealmsList();
    if (realmsList != null) {
      for (Iterator<Realm> iterator = realmsList.iterator(); iterator.hasNext(); ) {
        Realm realm = iterator.next();
        if (realm instanceof UserLookup) {
          UserLookup lookup = (UserLookup) realm;
          userList.addAll(lookup.lookupUsers(searchText));
        } else if (realm instanceof IniRealm) {
          // special handling for ini realm because it is the default 
          // when no other specific realm has been defined
          IniRealm r = (IniRealm) realm;
          userList.addAll(getIniUsersOrRoles(r, INI_SECTION_USERS));
        }
      }
    }
    
    // remove any users that don't contain the search text
    // this filters realms that didn't actually apply the filter
    for (Iterator<String> it = userList.iterator(); it.hasNext();) {
      if (!StringUtils.containsIgnoreCase(it.next(), searchText)) {
        it.remove();
      }
    }
    
    // return only the top N matches
    List<String> topResults = new ArrayList();
    int remaining = MAX_MATCHES_FROM_LOOKUP;
    for (Iterator<String> it = userList.iterator(); remaining-- > 0 && it.hasNext();) {
      topResults.add(it.next());
    }
    return topResults;
  }
  
  public static Collection<String> lookupRolesInRealms(String searchText) {
    Set<String> rolesList = new TreeSet<>(new PreferStartsWithComparator(searchText));
      
    // start by adding the authenticated user's roles
    rolesList.addAll(getRoles());
      
    // check if the configured realms support lookup
    Collection realmsList = SecurityUtils.getRealmsList();
    if (realmsList != null) {
      for (Iterator<Realm> iterator = realmsList.iterator(); iterator.hasNext(); ) {
        Realm realm = iterator.next();
        if (realm instanceof UserLookup) {
          UserLookup lookup = (UserLookup) realm;
          rolesList.addAll(lookup.lookupRoles(searchText));
        } else if (realm instanceof IniRealm) {
          // special handling for ini realm because it is the default 
          // when no other specific realm has been defined
          IniRealm r = (IniRealm) realm;
          rolesList.addAll(getIniUsersOrRoles(r, INI_SECTION_ROLES));
        }
      }
    }
    
    // remove any roles that don't contain the search text
    // this filters the authenticated user's roles and if one of the
    // realms didn't actually apply the filter
    for (Iterator<String> it = rolesList.iterator(); it.hasNext();) {
      if (!StringUtils.containsIgnoreCase(it.next(), searchText)) {
        it.remove();
      }
    }
      
    // return only the top N matches
    List<String> topResults = new ArrayList();
    int remaining = MAX_MATCHES_FROM_LOOKUP;
    for (Iterator<String> it = rolesList.iterator(); remaining-- > 0 && it.hasNext();) {
      topResults.add(it.next());
    }
    return topResults;
  }
  
  /**
   * Return a collection of the user or role names configured in the IniRealm.
   * @param realm
   * @param section "users" or "roles"
   * @return A collection of the user or role names. Possibly empty but not null.
   */
  private static Collection<String> getIniUsersOrRoles(IniRealm realm, String section) {
    Set<String> matches = new HashSet<>();
    Map entries = realm.getIni().get(section);
    if (entries != null) {
      for (Object key : entries.keySet()) {
        matches.add(key.toString().trim());
      }
    }
    return matches;
  }

  /**
   * Checked if shiro enabled or not
   */
  public static boolean isAuthenticated() {
    if (!isEnabled) {
      return false;
    }
    return org.apache.shiro.SecurityUtils.getSubject().isAuthenticated();
  }
  
  public static boolean isAnonymous() {
    return isAuthenticated() && ANONYMOUS.equals(getPrincipal());
  }
  
  /**
   * A Comparator that sorts matches, but prefer matches that start with the 
   * search text over those that just contain the search text
   */
  static class PreferStartsWithComparator implements Comparator<String> {
    
    private final String searchText;
    private final Collator collator = Collator.getInstance();
    
    PreferStartsWithComparator(String searchText) {
      this.searchText = searchText;
    }
      
    @Override
    public int compare(String o1, String o2) {
      boolean o1StartsWith = StringUtils.startsWithIgnoreCase(o1, searchText);
      boolean o2StartsWith = StringUtils.startsWithIgnoreCase(o2, searchText);
      if (o1StartsWith && o2StartsWith) {
        return collator.compare(o1, o2);
      } else if (o1StartsWith) {
        return -1;
      } else if (o2StartsWith) {
        return 1;
      } else {
        return collator.compare(o1, o2);
      }
    }
  }
}
