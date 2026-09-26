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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

class LdapRealmTest {
  @Test
  void testGetUserDn() {
    LdapRealm realm = new LdapRealm();

    // without a user search filter — when userDnTemplate is the bare
    // placeholder, the principal is treated as a full DN supplied verbatim
    // (no escape) so trailing-space inputs are preserved unchanged.
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
    // setUserSearchFilter / setGroupSearchFilter store the operator-supplied
    // template verbatim; user-controlled values are escaped at substitution
    // time by expandFilterTemplate, not at config time.
    realm.setUserSearchFilter("uid=<{0}>");
    assertEquals("uid=<{0}>", realm.getUserSearchFilter());
    realm.setUserSearchFilter("gid=\\{0}\\");
    assertEquals("gid=\\{0}\\", realm.getUserSearchFilter());
  }

  @Test
  void testRolesForMemberOfNestedGroups() throws NamingException {
    LdapRealm realm = new LdapRealm();
    realm.setGroupSearchEnableMemberOf(true);
    HashMap<String, String> rolesByGroups = new HashMap<>();
    rolesByGroups.put("nested-group", "nested-role");
    realm.setRolesByGroup(rolesByGroups);

    LdapContextFactory ldapContextFactory = mock(LdapContextFactory.class);
    LdapContext ldapCtx = mock(LdapContext.class);
    Session session = mock(Session.class);

    String userDn = realm.getUserDnForSearch("principal");

    // 389 DS MemberOf plugin already flattens direct + nested membership onto
    // the user entry, so a single base-scope search returns both group DNs.
    BasicAttribute memberOf = new BasicAttribute("memberOf");
    memberOf.add("cn=direct-group,cn=groups,cn=accounts,dc=example,dc=com");
    memberOf.add("cn=nested-group,cn=groups,cn=accounts,dc=example,dc=com");
    BasicAttributes userEntry = new BasicAttributes();
    userEntry.put(memberOf);

    NamingEnumeration<SearchResult> results = enumerationOf(userEntry);
    when(ldapCtx.search(eq(userDn), eq("(objectclass=*)"), any(SearchControls.class)))
        .thenReturn(results);

    Set<String> roles = realm.rolesFor(
        new SimplePrincipalCollection("principal", "ldapRealm"),
        "principal", ldapCtx, ldapContextFactory, session);

    assertEquals(new HashSet<>(Arrays.asList("direct-group", "nested-role")), roles);
  }

  @Test
  void testRolesForMatchingRuleInChainTakesPrecedenceOverMemberOf() throws NamingException {
    LdapRealm realm = new LdapRealm();
    realm.setGroupSearchEnableMatchingRuleInChain(true);
    realm.setGroupSearchEnableMemberOf(true);
    realm.setGroupSearchBase("cn=groups,dc=apache");

    LdapContextFactory ldapContextFactory = mock(LdapContextFactory.class);
    LdapContext ldapCtx = mock(LdapContext.class);
    Session session = mock(Session.class);

    BasicAttributes group1 = new BasicAttributes();
    group1.put(realm.getGroupIdAttribute(), "group-one");

    NamingEnumeration<SearchResult> results = enumerationOf(group1);
    when(ldapCtx.search(any(String.class), any(String.class), any(SearchControls.class)))
        .thenReturn(results);

    realm.rolesFor(
        new SimplePrincipalCollection("principal", "ldapRealm"),
        "principal", ldapCtx, ldapContextFactory, session);

    verify(ldapCtx, never()).search(anyString(), eq("(objectclass=*)"), any(SearchControls.class));
  }

  @Test
  void testRolesForMemberOfWithNoMemberOfAttribute() throws NamingException {
    LdapRealm realm = new LdapRealm();
    realm.setGroupSearchEnableMemberOf(true);

    LdapContextFactory ldapContextFactory = mock(LdapContextFactory.class);
    LdapContext ldapCtx = mock(LdapContext.class);
    Session session = mock(Session.class);

    String userDn = realm.getUserDnForSearch("principal");

    // The user entry is found, but it carries no memberOf attribute at all
    // (e.g. the user belongs to no groups) -> must not NPE, just no roles.
    BasicAttributes userEntry = new BasicAttributes();

    NamingEnumeration<SearchResult> results = enumerationOf(userEntry);
    when(ldapCtx.search(eq(userDn), eq("(objectclass=*)"), any(SearchControls.class)))
        .thenReturn(results);

    Set<String> roles = realm.rolesFor(
        new SimplePrincipalCollection("principal", "ldapRealm"),
        "principal", ldapCtx, ldapContextFactory, session);

    assertEquals(new HashSet<>(), roles);
  }

  @Test
  void testRolesForMemberOfWhenUserEntryNotFound() throws NamingException {
    LdapRealm realm = new LdapRealm();
    realm.setGroupSearchEnableMemberOf(true);

    LdapContextFactory ldapContextFactory = mock(LdapContextFactory.class);
    LdapContext ldapCtx = mock(LdapContext.class);
    Session session = mock(Session.class);

    String userDn = realm.getUserDnForSearch("principal");

    // The base-scope search for the user entry itself returns nothing
    // (e.g. the user DN doesn't exist) -> must not NPE, just no roles.
    NamingEnumeration<SearchResult> results = enumerationOf();
    when(ldapCtx.search(eq(userDn), eq("(objectclass=*)"), any(SearchControls.class)))
        .thenReturn(results);

    Set<String> roles = realm.rolesFor(
        new SimplePrincipalCollection("principal", "ldapRealm"),
        "principal", ldapCtx, ldapContextFactory, session);

    assertEquals(new HashSet<>(), roles);
  }

  @Test
  void testWarnBothGroupSearchModesLogsOnlyOnce() throws NamingException {
    LdapRealm realm = new LdapRealm();
    realm.setGroupSearchEnableMatchingRuleInChain(true);
    realm.setGroupSearchEnableMemberOf(true);
    realm.setGroupSearchBase("cn=groups,dc=apache");

    LdapContextFactory ldapContextFactory = mock(LdapContextFactory.class);
    LdapContext ldapCtx = mock(LdapContext.class);
    Session session = mock(Session.class);

    BasicAttributes group1 = new BasicAttributes();
    group1.put(realm.getGroupIdAttribute(), "group-one");

    // Fresh enumeration per call since NamingEnumeration is single-use.
    when(ldapCtx.search(any(String.class), any(String.class), any(SearchControls.class)))
        .thenAnswer(invocation -> enumerationOf(group1));

    // Repeated calls with both flags enabled must keep working the same way
    // after the WARN-once guard trips on the first call.
    Set<String> firstCall = realm.rolesFor(
        new SimplePrincipalCollection("principal", "ldapRealm"),
        "principal", ldapCtx, ldapContextFactory, session);
    Set<String> secondCall = realm.rolesFor(
        new SimplePrincipalCollection("principal", "ldapRealm"),
        "principal", ldapCtx, ldapContextFactory, session);

    assertEquals(firstCall, secondCall);
  }

  @Test
  void testGroupNameFromMemberOfDnFallback() {
    LdapRealm realm = new LdapRealm();

    // groupIdAttribute (default "cn") does not match any RDN type in the DN
    // below -> fall back to the leaf (left-most) RDN value.
    realm.setGroupIdAttribute("gidNumber");
    assertEquals("admins",
        realm.groupNameFromMemberOfDn("cn=admins,cn=groups,cn=accounts,dc=example,dc=com"));

    // A malformed DN must be skipped, not thrown, so one bad memberOf value
    // doesn't fail the whole login.
    assertNull(realm.groupNameFromMemberOfDn(",,,"));
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
