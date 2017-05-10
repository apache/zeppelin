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

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.crypto.hash.DefaultHashService;
import org.apache.shiro.crypto.hash.Hash;
import org.apache.shiro.crypto.hash.HashRequest;
import org.apache.shiro.crypto.hash.HashService;
import org.apache.shiro.realm.ldap.JndiLdapRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.apache.shiro.subject.MutablePrincipalCollection;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.PagedResultsControl;


/**
 * Implementation of {@link org.apache.shiro.realm.ldap.JndiLdapRealm} that also
 * returns each user's groups. This implementation is heavily based on
 * org.apache.isis.security.shiro.IsisLdapRealm.
 * 
 * <p>This implementation saves looked up ldap groups in Shiro Session to make them
 * easy to be looked up outside of this object
 * 
 * <p>Sample config for <tt>shiro.ini</tt>:
 * 
 * <p>[main] 
 * ldapRealm = org.apache.zeppelin.realm.LdapRealm
 * ldapRealm.contextFactory.url = ldap://localhost:33389
 * ldapRealm.contextFactory.authenticationMechanism = simple
 * ldapRealm.contextFactory.systemUsername = uid=guest,ou=people,dc=hadoop,dc=
 * apache,dc=org
 * ldapRealm.contextFactory.systemPassword = S{ALIAS=ldcSystemPassword}
 * ldapRealm.userDnTemplate = uid={0},ou=people,dc=hadoop,dc=apache,dc=org
 * # Ability to set ldap paging Size if needed default is 100
 * ldapRealm.pagingSize = 200
 * ldapRealm.authorizationEnabled = true
 * ldapRealm.searchBase = dc=hadoop,dc=apache,dc=org
 * ldapRealm.userSearchBase = dc=hadoop,dc=apache,dc=org
 * ldapRealm.groupSearchBase = ou=groups,dc=hadoop,dc=apache,dc=org
 * ldapRealm.userObjectClass = person
 * ldapRealm.groupObjectClass = groupofnames
 * # Allow userSearchAttribute to be customized
 * ldapRealm.userSearchAttributeName = sAMAccountName
 * ldapRealm.memberAttribute = member
 * # force usernames returned from ldap to lowercase useful for AD
 * ldapRealm.userLowerCase = true 
 * # ability set searchScopes subtree (default), one, base
 * ldapRealm.userSearchScope = subtree;
 * ldapRealm.groupSearchScope = subtree;
 * ldapRealm.memberAttributeValueTemplate=cn={0},ou=people,dc=hadoop,dc=apache,
 * dc=org
 * # enable support for nested groups using the LDAP_MATCHING_RULE_IN_CHAIN operator
 * ldapRealm.groupSearchEnableMatchingRuleInChain = true
 *
 * <p># optional mapping from physical groups to logical application roles
 * ldapRealm.rolesByGroup = \ LDN_USERS: user_role,\ NYK_USERS: user_role,\
 * HKG_USERS: user_role,\ GLOBAL_ADMIN: admin_role,\ DEMOS: self-install_role
 * 
 * <p>ldapRealm.permissionsByRole=\ user_role = *:ToDoItemsJdo:*:*,\
 * *:ToDoItem:*:*; \ self-install_role = *:ToDoItemsFixturesService:install:* ;
 * \ admin_role = *
 * 
 * <p>[urls]
 * **=authcBasic
 * 
 * <p>securityManager.realms = $ldapRealm
 * 
 */
public class LdapRealm extends JndiLdapRealm {

  private static final SearchControls SUBTREE_SCOPE = new SearchControls();
  private static final SearchControls ONELEVEL_SCOPE = new SearchControls();
  private static final SearchControls OBJECT_SCOPE = new SearchControls();
  private static final String SUBJECT_USER_ROLES = "subject.userRoles";
  private static final String SUBJECT_USER_GROUPS = "subject.userGroups";
  private static final String MEMBER_URL = "memberUrl";
  private static final String POSIX_GROUP = "posixGroup";
  
  // LDAP Operator '1.2.840.113556.1.4.1941'
  // walks the chain of ancestry in objects all the way to the root until it finds a match
  // see https://msdn.microsoft.com/en-us/library/aa746475(v=vs.85).aspx
  private static final String MATCHING_RULE_IN_CHAIN_FORMAT = 
      "(&(objectClass=%s)(%s:1.2.840.113556.1.4.1941:=%s))";

  private static Pattern TEMPLATE_PATTERN = Pattern.compile("\\{(\\d+?)\\}");
  private static String DEFAULT_PRINCIPAL_REGEX = "(.*)";
  private static final String MEMBER_SUBSTITUTION_TOKEN = "{0}";
  private static final String HASHING_ALGORITHM = "SHA-1";
  private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);


  static {
    SUBTREE_SCOPE.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ONELEVEL_SCOPE.setSearchScope(SearchControls.ONELEVEL_SCOPE);
    OBJECT_SCOPE.setSearchScope(SearchControls.OBJECT_SCOPE);
  }

  private String searchBase;
  private String userSearchBase;
  private int pagingSize = 100;
  private boolean userLowerCase;
  private String principalRegex = DEFAULT_PRINCIPAL_REGEX;
  private Pattern principalPattern = Pattern.compile(DEFAULT_PRINCIPAL_REGEX);
  private String userDnTemplate = "{0}";
  private String userSearchFilter = null;
  private String userSearchAttributeTemplate = "{0}";
  private String userSearchScope = "subtree";
  private String groupSearchScope = "subtree";
  private boolean groupSearchEnableMatchingRuleInChain;


  private String groupSearchBase;

  private String groupObjectClass = "groupOfNames";

  // typical value: member, uniqueMember, memberUrl
  private String memberAttribute = "member";

  private String groupIdAttribute = "cn";

  private String memberAttributeValuePrefix = "uid={0}";
  private String memberAttributeValueSuffix = "";

  private final Map<String, String> rolesByGroup = new LinkedHashMap<String, String>();
  private final Map<String, List<String>> permissionsByRole = 
      new LinkedHashMap<String, List<String>>();

  private boolean authorizationEnabled;

  private String userSearchAttributeName;
  private String userObjectClass = "person";

  private HashService hashService = new DefaultHashService();

  public LdapRealm() {
    HashedCredentialsMatcher credentialsMatcher = new HashedCredentialsMatcher(HASHING_ALGORITHM);
    setCredentialsMatcher(credentialsMatcher);
  }

  @Override

  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws org.apache.shiro.authc.AuthenticationException {
    try {
      return super.doGetAuthenticationInfo(token);
    } catch (org.apache.shiro.authc.AuthenticationException ae) {
      throw ae;
    }
  }

  /**
  * Get groups from LDAP.
  * 
  * @param principals
  *            the principals of the Subject whose AuthenticationInfo should
  *            be queried from the LDAP server.
  * @param ldapContextFactory
  *            factory used to retrieve LDAP connections.
  * @return an {@link AuthorizationInfo} instance containing information
  *         retrieved from the LDAP server.
  * @throws NamingException
  *             if any LDAP errors occur during the search.
  */
  @Override
  protected AuthorizationInfo queryForAuthorizationInfo(final PrincipalCollection principals,
      final LdapContextFactory ldapContextFactory) throws NamingException {
    if (!isAuthorizationEnabled()) {
      return null;
    }
    final Set<String> roleNames = getRoles(principals, ldapContextFactory);
    if (log.isDebugEnabled()) {
      log.debug("RolesNames Authorization: " + roleNames);
    }
    SimpleAuthorizationInfo simpleAuthorizationInfo = new SimpleAuthorizationInfo(roleNames);
    Set<String> stringPermissions = permsFor(roleNames);
    simpleAuthorizationInfo.setStringPermissions(stringPermissions);
    return simpleAuthorizationInfo;
  }

  private Set<String> getRoles(PrincipalCollection principals, 
        final LdapContextFactory ldapContextFactory)
      throws NamingException {
    final String username = (String) getAvailablePrincipal(principals);

    LdapContext systemLdapCtx = null;
    try {
      systemLdapCtx = ldapContextFactory.getSystemLdapContext();
      return rolesFor(principals, username, systemLdapCtx, ldapContextFactory);
    } catch (AuthenticationException ae) {
      ae.printStackTrace();
      return Collections.emptySet();
    } finally {
      LdapUtils.closeContext(systemLdapCtx);
    }
  }

  private Set<String> rolesFor(PrincipalCollection principals, 
        String userNameIn, final LdapContext ldapCtx,
      final LdapContextFactory ldapContextFactory) throws NamingException {
    final Set<String> roleNames = new HashSet<>();
    final Set<String> groupNames = new HashSet<>();
    final String userName;
    if (getUserLowerCase()) {
      log.debug("userLowerCase true");
      userName = userNameIn.toLowerCase();
    } else {
      userName = userNameIn;
    }
    
    String userDn;
    if (userSearchAttributeName == null || userSearchAttributeName.isEmpty()) {
      // memberAttributeValuePrefix and memberAttributeValueSuffix 
      // were computed from memberAttributeValueTemplate
      userDn = memberAttributeValuePrefix + userName + memberAttributeValueSuffix;      
    } else {
      userDn = getUserDn(userName);
    }

    // Activate paged results
    int pageSize = getPagingSize();
    if (log.isDebugEnabled()) {
      log.debug("Ldap PagingSize: " + pageSize);
    }
    int numResults = 0;
    byte[] cookie = null;
    try {
      ldapCtx.addToEnvironment(Context.REFERRAL, "ignore");
        
      ldapCtx.setRequestControls(new Control[]{new PagedResultsControl(pageSize, 
            Control.NONCRITICAL)});
        
      do {
        // ldapsearch -h localhost -p 33389 -D
        // uid=guest,ou=people,dc=hadoop,dc=apache,dc=org -w guest-password
        // -b dc=hadoop,dc=apache,dc=org -s sub '(objectclass=*)'
        NamingEnumeration<SearchResult> searchResultEnum = null;
        SearchControls searchControls = getGroupSearchControls();
        try {
          if (groupSearchEnableMatchingRuleInChain) {
            searchResultEnum = ldapCtx.search(
                getGroupSearchBase(),
                String.format(
                    MATCHING_RULE_IN_CHAIN_FORMAT, groupObjectClass, memberAttribute, userDn),
                searchControls);
            while (searchResultEnum != null && searchResultEnum.hasMore()) { 
              // searchResults contains all the groups in search scope
              numResults++;
              final SearchResult group = searchResultEnum.next();

              Attribute attribute = group.getAttributes().get(getGroupIdAttribute());
              String groupName = attribute.get().toString();            
              
              String roleName = roleNameFor(groupName);
              if (roleName != null) {
                roleNames.add(roleName);
              } else {
                roleNames.add(groupName);
              }
            }                
          } else {
            searchResultEnum = ldapCtx.search(
                getGroupSearchBase(),
                "objectClass=" + groupObjectClass,
                searchControls);
            while (searchResultEnum != null && searchResultEnum.hasMore()) { 
              // searchResults contains all the groups in search scope
              numResults++;
              final SearchResult group = searchResultEnum.next();
              addRoleIfMember(userDn, group, roleNames, groupNames, ldapContextFactory);
            }
          }
        } catch (PartialResultException e) {
          log.debug("Ignoring PartitalResultException");
        } finally {
          if (searchResultEnum != null) {
            searchResultEnum.close();
          }
        }
        // Re-activate paged results
        ldapCtx.setRequestControls(new Control[]{new PagedResultsControl(pageSize, 
              cookie, Control.CRITICAL)});
      } while (cookie != null);
    } catch (SizeLimitExceededException e) {
      log.info("Only retrieved first " + numResults + 
            " groups due to SizeLimitExceededException.");
    } catch (IOException e) {
      log.error("Unabled to setup paged results");
    }
    // save role names and group names in session so that they can be
    // easily looked up outside of this object
    SecurityUtils.getSubject().getSession().setAttribute(SUBJECT_USER_ROLES, roleNames);
    SecurityUtils.getSubject().getSession().setAttribute(SUBJECT_USER_GROUPS, groupNames);
    if (!groupNames.isEmpty() && (principals instanceof MutablePrincipalCollection)) {
      ((MutablePrincipalCollection) principals).addAll(groupNames, getName());
    }
    if (log.isDebugEnabled()) {
      log.debug("User RoleNames: " + userName + "::" + roleNames);  
    }
    return roleNames;
  }

  private void addRoleIfMember(final String userDn, final SearchResult group, 
        final Set<String> roleNames, final Set<String> groupNames, 
        final LdapContextFactory ldapContextFactory) throws NamingException {

    NamingEnumeration<? extends Attribute> attributeEnum = null;
    NamingEnumeration<?> ne = null;
    try {
      LdapName userLdapDn = new LdapName(userDn);
      Attribute attribute = group.getAttributes().get(getGroupIdAttribute());
      String groupName = attribute.get().toString();

      attributeEnum = group.getAttributes().getAll();
      while (attributeEnum.hasMore()) {
        final Attribute attr = attributeEnum.next();
        if (!memberAttribute.equalsIgnoreCase(attr.getID())) {
          continue;
        }
        ne = attr.getAll();
        while (ne.hasMore()) {
          String attrValue = ne.next().toString();
          if (memberAttribute.equalsIgnoreCase(MEMBER_URL)) {
            boolean dynamicGroupMember = isUserMemberOfDynamicGroup(userLdapDn, attrValue,
                  ldapContextFactory);
            if (dynamicGroupMember) {
              groupNames.add(groupName);
              String roleName = roleNameFor(groupName);
              if (roleName != null) {
                roleNames.add(roleName);
              } else {
                roleNames.add(groupName);
              }
            }
          } else {
            if (groupObjectClass.equalsIgnoreCase(POSIX_GROUP)) {
              attrValue = memberAttributeValuePrefix + attrValue + memberAttributeValueSuffix;
            }
            if (userLdapDn.equals(new LdapName(attrValue))) {
              groupNames.add(groupName);
              String roleName = roleNameFor(groupName);
              if (roleName != null) {
                roleNames.add(roleName);
              } else {
                roleNames.add(groupName);
              }
              break;
            }
          }
        }
      }
    } finally {
      try {
        if (attributeEnum != null) {
          attributeEnum.close();
        }
      } finally {
        if (ne != null) {
          ne.close();
        }
      }
    }
  }
  
  public Map<String, String> getListRoles() {
    Map<String, String> groupToRoles = getRolesByGroup();
    Map<String, String> roles = new HashMap<>();
    for (Map.Entry<String, String> entry : groupToRoles.entrySet()){
      roles.put(entry.getValue(), entry.getKey());
    }
    return roles;
  }

  private String roleNameFor(String groupName) {
    return !rolesByGroup.isEmpty() ? rolesByGroup.get(groupName) : groupName;
  }

  private Set<String> permsFor(Set<String> roleNames) {
    Set<String> perms = new LinkedHashSet<String>(); // preserve order
    for (String role : roleNames) {
      List<String> permsForRole = permissionsByRole.get(role);
      if (log.isDebugEnabled()) {
        log.debug("PermsForRole: " + role);
        log.debug("PermByRole: " + permsForRole);
      }
      if (permsForRole != null) {
        perms.addAll(permsForRole);
      }
    }
    return perms;
  }

  public String getSearchBase() {
    return searchBase;
  }

  public void setSearchBase(String searchBase) {
    this.searchBase = searchBase;
  }

  public String getUserSearchBase() {
    return (userSearchBase != null && !userSearchBase.isEmpty()) ? userSearchBase : searchBase;
  }

  public void setUserSearchBase(String userSearchBase) {
    this.userSearchBase = userSearchBase;
  }

  public int getPagingSize() {
    return pagingSize;
  }
  
  public void setPagingSize(int pagingSize) {
    this.pagingSize = pagingSize;
  }

  public String getGroupSearchBase() {
    return (groupSearchBase != null && !groupSearchBase.isEmpty()) ? groupSearchBase : searchBase;
  }

  public void setGroupSearchBase(String groupSearchBase) {
    this.groupSearchBase = groupSearchBase;
  }

  public String getGroupObjectClass() {
    return groupObjectClass;
  }

  public void setGroupObjectClass(String groupObjectClassAttribute) {
    this.groupObjectClass = groupObjectClassAttribute;
  }

  public String getMemberAttribute() {
    return memberAttribute;
  }

  public void setMemberAttribute(String memberAttribute) {
    this.memberAttribute = memberAttribute;
  }

  public String getGroupIdAttribute() {
    return groupIdAttribute;
  }

  public void setGroupIdAttribute(String groupIdAttribute) {
    this.groupIdAttribute = groupIdAttribute;
  }
  
  /**
  * Set Member Attribute Template for LDAP.
  * 
  * @param template
  *            DN template to be used to query ldap.
  * @throws IllegalArgumentException
  *             if template is empty or null.
  */
  public void setMemberAttributeValueTemplate(String template) {
    if (!StringUtils.hasText(template)) {
      String msg = "User DN template cannot be null or empty.";
      throw new IllegalArgumentException(msg);
    }
    int index = template.indexOf(MEMBER_SUBSTITUTION_TOKEN);
    if (index < 0) {
      String msg = "Member attribute value template must contain the '" + MEMBER_SUBSTITUTION_TOKEN
            + "' replacement token to understand how to " + "parse the group members.";
      throw new IllegalArgumentException(msg);
    }
    String prefix = template.substring(0, index);
    String suffix = template.substring(prefix.length() + MEMBER_SUBSTITUTION_TOKEN.length());
    this.memberAttributeValuePrefix = prefix;
    this.memberAttributeValueSuffix = suffix;
  }

  public void setRolesByGroup(Map<String, String> rolesByGroup) {
    this.rolesByGroup.putAll(rolesByGroup);
  }
  
  public Map<String, String> getRolesByGroup() {
    return rolesByGroup;
  }

  public void setPermissionsByRole(String permissionsByRoleStr) {
    permissionsByRole.putAll(parsePermissionByRoleString(permissionsByRoleStr));
  }
  
  public Map<String, List<String>> getPermissionsByRole() {
    return permissionsByRole;
  }

  public boolean isAuthorizationEnabled() {
    return authorizationEnabled;
  }

  public void setAuthorizationEnabled(boolean authorizationEnabled) {
    this.authorizationEnabled = authorizationEnabled;
  }

  public String getUserSearchAttributeName() {
    return userSearchAttributeName;
  }
  
  /**
  * Set User Search Attribute Name for LDAP.
  * 
  * @param userSearchAttributeName
  *            userAttribute to search ldap.
  */
  public void setUserSearchAttributeName(String userSearchAttributeName) {
    if (userSearchAttributeName != null) {
      userSearchAttributeName = userSearchAttributeName.trim();
    }
    this.userSearchAttributeName = userSearchAttributeName;
  }

  public String getUserObjectClass() {
    return userObjectClass;
  }

  public void setUserObjectClass(String userObjectClass) {
    this.userObjectClass = userObjectClass;
  }

  private Map<String, List<String>> parsePermissionByRoleString(String permissionsByRoleStr) {
    Map<String, List<String>> perms = new HashMap<String, List<String>>();

    // split by semicolon ; then by eq = then by comma ,
    StringTokenizer stSem = new StringTokenizer(permissionsByRoleStr, ";");
    while (stSem.hasMoreTokens()) {
      String roleAndPerm = stSem.nextToken();
      StringTokenizer stEq = new StringTokenizer(roleAndPerm, "=");
      if (stEq.countTokens() != 2) {
        continue;
      }
      String role = stEq.nextToken().trim();
      String perm = stEq.nextToken().trim();
      StringTokenizer stCom = new StringTokenizer(perm, ",");
      List<String> permList = new ArrayList<String>();
      while (stCom.hasMoreTokens()) {
        permList.add(stCom.nextToken().trim());
      }
      perms.put(role, permList);
    }
    return perms;
  }

  boolean isUserMemberOfDynamicGroup(LdapName userLdapDn, String memberUrl,
        final LdapContextFactory ldapContextFactory) throws NamingException {

    // ldap://host:port/dn?attributes?scope?filter?extensions

    if (memberUrl == null) {
      return false;
    }
    String[] tokens = memberUrl.split("\\?");
    if (tokens.length < 4) {
      return false;
    }

    String searchBaseString = tokens[0].substring(tokens[0].lastIndexOf("/") + 1);
    String searchScope = tokens[2];
    String searchFilter = tokens[3];

    LdapName searchBaseDn = new LdapName(searchBaseString);

    // do scope test
    if (searchScope.equalsIgnoreCase("base")) {
      log.debug("DynamicGroup SearchScope base");
      return false;
    }
    if (!userLdapDn.toString().endsWith(searchBaseDn.toString())) {
      return false;
    }
    if (searchScope.equalsIgnoreCase("one") && (userLdapDn.size() != searchBaseDn.size() - 1)) {
      log.debug("DynamicGroup SearchScope one");
      return false;
    }
    // search for the filter, substituting base with userDn
    // search for base_dn=userDn, scope=base, filter=filter
    LdapContext systemLdapCtx = null;
    systemLdapCtx = ldapContextFactory.getSystemLdapContext();
    boolean member = false;
    NamingEnumeration<SearchResult> searchResultEnum = null;
    try {
      searchResultEnum = systemLdapCtx.search(userLdapDn, searchFilter,
            searchScope.equalsIgnoreCase("sub") ? SUBTREE_SCOPE : ONELEVEL_SCOPE);
      if (searchResultEnum.hasMore()) {
        return true;
      }
    } finally {
      try {
        if (searchResultEnum != null) {
          searchResultEnum.close();
        }
      } finally {
        LdapUtils.closeContext(systemLdapCtx);
      }
    }
    return member;
  }

  public String getPrincipalRegex() {
    return principalRegex;
  }
  
  /**
  * Set Regex for Principal LDAP.
  * 
  * @param regex
  *            regex to use to search for principal in shiro.
  */
  public void setPrincipalRegex(String regex) {
    if (regex == null || regex.trim().isEmpty()) {
      principalPattern = Pattern.compile(DEFAULT_PRINCIPAL_REGEX);
      principalRegex = DEFAULT_PRINCIPAL_REGEX;
    } else {
      regex = regex.trim();
      Pattern pattern = Pattern.compile(regex);
      principalPattern = pattern;
      principalRegex = regex;
    }
  }

  public String getUserSearchAttributeTemplate() {
    return userSearchAttributeTemplate;
  }

  public void setUserSearchAttributeTemplate(final String template) {
    this.userSearchAttributeTemplate = (template == null ? null : template.trim());
  }

  public String getUserSearchFilter() {
    return userSearchFilter;
  }

  public void setUserSearchFilter(final String filter) {
    this.userSearchFilter = (filter == null ? null : filter.trim());
  }
  
  public boolean getUserLowerCase() {
    return userLowerCase;
  }
  
  public void setUserLowerCase(boolean userLowerCase) {
    this.userLowerCase = userLowerCase;
  }
  
  public String getUserSearchScope() {
    return userSearchScope;
  }

  public void setUserSearchScope(final String scope) {
    this.userSearchScope = (scope == null ? null : scope.trim().toLowerCase());
  }

  public String getGroupSearchScope() {
    return groupSearchScope;
  }

  public void setGroupSearchScope(final String scope) {
    this.groupSearchScope = (scope == null ? null : scope.trim().toLowerCase());
  }
  
  public boolean isGroupSearchEnableMatchingRuleInChain() {
    return groupSearchEnableMatchingRuleInChain;
  }

  public void setGroupSearchEnableMatchingRuleInChain(
      boolean groupSearchEnableMatchingRuleInChain) {
    this.groupSearchEnableMatchingRuleInChain = groupSearchEnableMatchingRuleInChain;
  }

  private SearchControls getUserSearchControls() {
    SearchControls searchControls = SUBTREE_SCOPE;
    if ("onelevel".equalsIgnoreCase(userSearchScope)) {
      searchControls = ONELEVEL_SCOPE;
    } else if ("object".equalsIgnoreCase(userSearchScope)) {
      searchControls = OBJECT_SCOPE;
    }
    return searchControls;
  }
  
  private SearchControls getGroupSearchControls() {
    SearchControls searchControls = SUBTREE_SCOPE;
    if ("onelevel".equalsIgnoreCase(groupSearchScope)) {
      searchControls = ONELEVEL_SCOPE;
    } else if ("object".equalsIgnoreCase(groupSearchScope)) {
      searchControls = OBJECT_SCOPE;
    }
    return searchControls;
  }

  @Override
  public void setUserDnTemplate(final String template) throws IllegalArgumentException {
    userDnTemplate = template;
  }

  private Matcher matchPrincipal(final String principal) {
    Matcher matchedPrincipal = principalPattern.matcher(principal);
    if (!matchedPrincipal.matches()) {
      throw new IllegalArgumentException("Principal " 
            + principal + " does not match " + principalRegex);
    }
    return matchedPrincipal;
  }

  /**
  * Returns the LDAP User Distinguished Name (DN) to use when acquiring an
  * {@link javax.naming.ldap.LdapContext LdapContext} from the
  * {@link LdapContextFactory}.
  * <p/>
  * If the the {@link #getUserDnTemplate() userDnTemplate} property has been
  * set, this implementation will construct the User DN by substituting the
  * specified {@code principal} into the configured template. If the
  * {@link #getUserDnTemplate() userDnTemplate} has not been set, the method
  * argument will be returned directly (indicating that the submitted
  * authentication token principal <em>is</em> the User DN).
  *
  * @param principal
  *            the principal to substitute into the configured
  *            {@link #getUserDnTemplate() userDnTemplate}.
  * @return the constructed User DN to use at runtime when acquiring an
  *         {@link javax.naming.ldap.LdapContext}.
  * @throws IllegalArgumentException
  *             if the method argument is null or empty
  * @throws IllegalStateException
  *             if the {@link #getUserDnTemplate userDnTemplate} has not been
  *             set.
  * @see LdapContextFactory#getLdapContext(Object, Object)
  */
  @Override
  protected String getUserDn(final String principal) throws IllegalArgumentException, 
        IllegalStateException {
    String userDn;
    Matcher matchedPrincipal = matchPrincipal(principal);
    String userSearchBase = getUserSearchBase();
    String userSearchAttributeName = getUserSearchAttributeName();

    // If not searching use the userDnTemplate and return.
    if ((userSearchBase == null || userSearchBase.isEmpty()) || (userSearchAttributeName == null
          && userSearchFilter == null && !"object".equalsIgnoreCase(userSearchScope))) {
      userDn = expandTemplate(userDnTemplate, matchedPrincipal);
      if (log.isDebugEnabled()) {
        log.debug("LDAP UserDN and Principal: " + userDn + "," + principal);
      }
      return userDn;
    }

    // Create the searchBase and searchFilter from config.
    String searchBase = expandTemplate(getUserSearchBase(), matchedPrincipal);
    String searchFilter = null;
    if (userSearchFilter == null) {
      if (userSearchAttributeName == null) {
        searchFilter = String.format("(objectclass=%1$s)", getUserObjectClass());
      } else {
        searchFilter = String.format("(&(objectclass=%1$s)(%2$s=%3$s))", getUserObjectClass(),
              userSearchAttributeName, expandTemplate(getUserSearchAttributeTemplate(), 
              matchedPrincipal));
      }
    } else {
      searchFilter = expandTemplate(userSearchFilter, matchedPrincipal);
    }
    SearchControls searchControls = getUserSearchControls();

    // Search for userDn and return.
    LdapContext systemLdapCtx = null;
    NamingEnumeration<SearchResult> searchResultEnum = null;
    try {
      systemLdapCtx = getContextFactory().getSystemLdapContext();
      if (log.isDebugEnabled()) {
        log.debug("SearchBase,SearchFilter,UserSearchScope: " + searchBase 
              + "," + searchFilter + "," + userSearchScope);
      }
      searchResultEnum = systemLdapCtx.search(searchBase, searchFilter, searchControls);
      // SearchResults contains all the entries in search scope
      if (searchResultEnum.hasMore()) {
        SearchResult searchResult = searchResultEnum.next();
        userDn = searchResult.getNameInNamespace();
        if (log.isDebugEnabled()) {
          log.debug("UserDN Returned,Principal: " + userDn + "," + principal);
        }
        return userDn;
      } else {
        throw new IllegalArgumentException("Illegal principal name: " + principal);
      }
    } catch (AuthenticationException ne) {
      ne.printStackTrace();
      throw new IllegalArgumentException("Illegal principal name: " + principal);
    } catch (NamingException ne) {
      throw new IllegalArgumentException("Hit NamingException: " + ne.getMessage());
    } finally {
      try {
        if (searchResultEnum != null) {
          searchResultEnum.close();
        }
      } catch (NamingException ne) {
        // Ignore exception on close.
      } finally {
        LdapUtils.closeContext(systemLdapCtx);
      }
    }
  }

  @Override
  protected AuthenticationInfo createAuthenticationInfo(AuthenticationToken token, 
        Object ldapPrincipal,
        Object ldapCredentials, LdapContext ldapContext) throws NamingException {
    HashRequest.Builder builder = new HashRequest.Builder();
    Hash credentialsHash = hashService
          .computeHash(builder.setSource(token.getCredentials())
                .setAlgorithmName(HASHING_ALGORITHM).build());
    return new SimpleAuthenticationInfo(token.getPrincipal(), 
          credentialsHash.toHex(), credentialsHash.getSalt(),
          getName());
  }

  private static final String expandTemplate(final String template, final Matcher input) {
    String output = template;
    Matcher matcher = TEMPLATE_PATTERN.matcher(output);
    while (matcher.find()) {
      String lookupStr = matcher.group(1);
      int lookupIndex = Integer.parseInt(lookupStr);
      String lookupValue = input.group(lookupIndex);
      output = matcher.replaceFirst(lookupValue == null ? "" : lookupValue);
      matcher = TEMPLATE_PATTERN.matcher(output);
    }
    return output;
  }
}
