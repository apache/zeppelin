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

import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;


public class LdapRealmTest {

  @Test
  public void testGetUserDn() {
    LdapRealm realm = new LdapRealm();

    // without a user search filter
    realm.setUserSearchFilter(null);
    assertEquals(
      "foo ",
      realm.getUserDn("foo ")
    );

    // with a user search filter
    realm.setUserSearchFilter("memberUid={0}");
    assertEquals(
      "foo",
      realm.getUserDn("foo")
    );
  }

  @Test
  public void testExpandTemplate() {
    assertEquals(
      "uid=foo,cn=users,dc=ods,dc=foo",
      LdapRealm.expandTemplate("uid={0},cn=users,dc=ods,dc=foo", "foo")
    );
  }

  @Test
  public void getUserDnForSearch() {
    LdapRealm realm = new LdapRealm();

    realm.setUserSearchAttributeName("uid");
    assertEquals(
      "foo",
      realm.getUserDnForSearch("foo")
    );

    // using a template
    realm.setUserSearchAttributeName(null);
    realm.setMemberAttributeValueTemplate("cn={0},ou=people,dc=hadoop,dc=apache");
    assertEquals(
      "cn=foo,ou=people,dc=hadoop,dc=apache",
      realm.getUserDnForSearch("foo")
    );
  }

  @Test
  public void testRolesFor() throws NamingException {
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
    when(ldapCtx.search(any(String.class), any(String.class), any(SearchControls.class))).thenReturn(results);


    Set<String> roles = realm.rolesFor(
      new SimplePrincipalCollection("principal", "ldapRealm"),
      "principal",
      ldapCtx,
      ldapContextFactory,
      session
    );

    verify(ldapCtx).search(
      "cn=groups,dc=apache",
      "(objectclass=posixGroup)",
      realm.getGroupSearchControls()
    );

    assertEquals(
      new HashSet(Arrays.asList("group-one", "zeppelin-role")),
      roles
    );
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
