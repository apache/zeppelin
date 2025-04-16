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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

class LdapRealmTest {
  @Test
  void testGetUserDn() {
    LdapRealm realm = new LdapRealm();

    // without a user search filter
    realm.setUserSearchFilter(null);
    assertEquals("foo ", realm.getUserDn("foo "));

    // with a user search filter
    realm.setUserSearchFilter("memberUid={0}");
    assertEquals("foo", realm.getUserDn("foo"));
  }

  @Test
  void testExpandTemplate() {
    assertEquals("uid=foo,cn=users,dc=ods,dc=foo",
            LdapRealm.expandTemplate("uid={0},cn=users,dc=ods,dc=foo", "foo"));
  }

  @Test
  void getUserDnForSearch() {
    LdapRealm realm = new LdapRealm();

    realm.setUserSearchAttributeName("uid");
    assertEquals("foo", realm.getUserDnForSearch("foo"));

    // using a template
    realm.setUserSearchAttributeName(null);
    realm.setMemberAttributeValueTemplate("cn={0},ou=people,dc=hadoop,dc=apache");
    assertEquals("cn=foo,ou=people,dc=hadoop,dc=apache",
            realm.getUserDnForSearch("foo"));
  }

  @Test
  void testRolesFor() throws NamingException {
    LdapRealm realm = new LdapRealm();
    realm.setGroupSearchBase("cn=groups,dc=apache");
    realm.setGroupObjectClass("posixGroup");
    realm.setMemberAttributeValueTemplate("cn={0},ou=people,dc=apache");
    HashMap<String, String> rolesByGroups = new HashMap<>();
    rolesByGroups.put("group-three", "zeppelin-role");
    realm.setRolesByGroup(rolesByGroups);

    LdapContextFactory ldapContextFactory = mock(LdapContextFactory.class);
    LdapContext ldapCtx = mock(LdapContext.class);
    Session session = mock(Session.class);

    // expected search results
    BasicAttributes group1 = new BasicAttributes();
    group1.put(realm.getGroupIdAttribute(), "group-one");
    group1.put(realm.getMemberAttribute(), "principal");

    // user doesn't belong to this group
    BasicAttributes group2 = new BasicAttributes();
    group2.put(realm.getGroupIdAttribute(), "group-two");
    group2.put(realm.getMemberAttribute(), "someoneelse");

    // mapped to a different Zeppelin role
    BasicAttributes group3 = new BasicAttributes();
    group3.put(realm.getGroupIdAttribute(), "group-three");
    group3.put(realm.getMemberAttribute(), "principal");

    NamingEnumeration<SearchResult> results = enumerationOf(group1, group2, group3);
    when(ldapCtx.search(any(String.class), any(String.class), any(SearchControls.class)))
            .thenReturn(results);

    Set<String> roles = realm.rolesFor(
            new SimplePrincipalCollection("principal", "ldapRealm"),
            "principal", ldapCtx, ldapContextFactory, session);

    verify(ldapCtx).search("cn=groups,dc=apache", "(objectclass=posixGroup)",
            realm.getGroupSearchControls());

    assertEquals(new HashSet<>(Arrays.asList("group-one", "zeppelin-role")), roles);
  }

  @Test
  void testFilterEscaping() {
    LdapRealm realm = new LdapRealm();
    assertEquals("foo", realm.escapeAttributeValue("foo"));
    assertEquals("foo\\2B", realm.escapeAttributeValue("foo+"));
    assertEquals("foo\\5C", realm.escapeAttributeValue("foo\\"));
    assertEquals("foo\\00", realm.escapeAttributeValue("foo\u0000"));
    realm.setUserSearchFilter("uid=<{0}>");
    assertEquals("uid=\\3C{0}\\3E", realm.getUserSearchFilter());
    realm.setUserSearchFilter("gid=\\{0}\\");
    assertEquals("gid=\\5C{0}\\5C", realm.getUserSearchFilter());
  }

  @Test
  void testMemberOfAttributeRolesFor() throws NamingException {
    LdapRealm realm = new LdapRealm();
    realm.setUseMemberOfForNestedGroups(true);
    realm.setMemberOfAttribute("memberOf");
    realm.setGroupIdAttribute("cn");
    realm.setUserSearchBase("cn=users,cn=accounts,dc=ipa,dc=mgt");
    realm.setUserObjectClass("person");
    realm.setUserSearchAttributeName("uid");

    HashMap<String, String> rolesByGroups = new HashMap<>();
    rolesByGroups.put("res_zeppelin_sqx_admin", "admins_role");
    rolesByGroups.put("ipausers", "users_role");
    realm.setRolesByGroup(rolesByGroups);

    LdapContextFactory ldapContextFactory = mock(LdapContextFactory.class);
    LdapContext ldapCtx = mock(LdapContext.class);
    Session session = mock(Session.class);

    // Create user with memberOf attributes
    BasicAttributes userAttrs = new BasicAttributes();
    userAttrs.put("uid", "testuser");
    
    // Add memberOf attribute with two group DNs
    javax.naming.directory.BasicAttribute memberOfAttr = 
        new javax.naming.directory.BasicAttribute("memberOf");
    memberOfAttr.add("cn=res_zeppelin_sqx_admin,cn=groups,cn=accounts,dc=ipa,dc=mgt");
    memberOfAttr.add("cn=ipausers,cn=groups,cn=accounts,dc=ipa,dc=mgt");
    userAttrs.put(memberOfAttr);
    
    // Create SearchResult with the user attributes
    SearchResult userSearchResult = new SearchResult(
        "uid=testuser,cn=users,cn=accounts,dc=ipa,dc=mgt", null, userAttrs);

    // Configure mock to return our test user when searching
    NamingEnumeration<SearchResult> userSearchResults = enumerationOf(userAttrs);
    when(ldapCtx.search(any(String.class), any(String.class), any(SearchControls.class)))
        .thenReturn(userSearchResults);

    // Call the method being tested
    Set<String> roles = realm.rolesFor(
        new SimplePrincipalCollection("testuser", "ldapRealm"),
        "testuser", ldapCtx, ldapContextFactory, session);

    // Verify correct roles are assigned based on memberOf attribute values
    Set<String> expectedRoles = new HashSet<>(Arrays.asList("admins_role", "users_role"));
    assertEquals(expectedRoles, roles);
    
    // Verify session attributes are set with the correct roles and groups
    verify(session).setAttribute("subject.userRoles", roles);
    verify(session).setAttribute(any(String.class), any(Set.class));
  }

  @Test
  void testGroupNameExtractionFromDN() throws Exception {
    LdapRealm realm = new LdapRealm();
    realm.setUseMemberOfForNestedGroups(true);
    realm.setMemberOfAttribute("memberOf");
    realm.setGroupIdAttribute("cn");
    
    // Create test data with different DN structures
    LdapContextFactory ldapContextFactory = mock(LdapContextFactory.class);
    LdapContext ldapCtx = mock(LdapContext.class);
    Session session = mock(Session.class);

    // Create user with complex memberOf structure
    BasicAttributes userAttrs = new BasicAttributes();
    userAttrs.put("uid", "testuser");
    
    // Add memberOf attribute with various DN formats to test extraction logic
    javax.naming.directory.BasicAttribute memberOfAttr = 
        new javax.naming.directory.BasicAttribute("memberOf");
    // Standard group DN - cn is the leftmost part
    memberOfAttr.add("cn=standard_group,ou=groups,dc=example,dc=com");
    // Complex DN - where cn appears multiple times, should extract the leftmost one
    memberOfAttr.add("cn=admin_group,cn=nested,ou=groups,dc=example,dc=com");
    // DN with cn in middle - tests the fix for component ordering
    memberOfAttr.add("ou=external,cn=special_group,dc=example,dc=com");
    userAttrs.put(memberOfAttr);
    
    // Set up role mappings to verify behavior
    HashMap<String, String> rolesByGroups = new HashMap<>();
    rolesByGroups.put("standard_group", "standard_role");
    rolesByGroups.put("admin_group", "admin_role");
    rolesByGroups.put("special_group", "special_role");
    realm.setRolesByGroup(rolesByGroups);
    
    // Configure mock to return our test user when searching
    NamingEnumeration<SearchResult> userSearchResults = enumerationOf(userAttrs);
    when(ldapCtx.search(any(String.class), any(String.class), any(SearchControls.class)))
        .thenReturn(userSearchResults);

    // Call the method being tested
    Set<String> roles = realm.rolesFor(
        new SimplePrincipalCollection("testuser", "ldapRealm"),
        "testuser", ldapCtx, ldapContextFactory, session);

    // Verify all group names were correctly extracted from the DNs
    Set<String> expectedRoles = new HashSet<>(Arrays.asList(
        "standard_role", "admin_role", "special_role"));
    assertEquals(expectedRoles, roles);
  }

  private NamingEnumeration<SearchResult> enumerationOf(BasicAttributes... attrs) {
    final Iterator<BasicAttributes> iterator = Arrays.asList(attrs).iterator();
    return new NamingEnumeration<SearchResult>() {
      @Override
      public SearchResult next() throws NamingException {
        return nextElement();
      }

      @Override
      public boolean hasMore() throws NamingException {
        return iterator.hasNext();
      }

      @Override
      public void close() throws NamingException {
      }

      @Override
      public boolean hasMoreElements() {
        return iterator.hasNext();
      }

      @Override
      public SearchResult nextElement() {
        final BasicAttributes attrs = iterator.next();
        return new SearchResult(null, null, attrs);
      }
    };
  }
}
