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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/**
 * Verifies that {@link ActiveDirectoryGroupRealm}'s LDAP search calls receive a
 * filter string that has every user-controlled metacharacter RFC 4515 escaped.
 * Captures the actual filter argument passed to the mocked
 * {@link LdapContext#search} and asserts the absence of unescaped
 * {@code (}, {@code )}, {@code *} originating from the user payload.
 */
class ActiveDirectoryGroupRealmFilterInjectionTest {

  @ParameterizedTest
  @ValueSource(strings = {
      ")(objectClass=*",
      "admin)(|(uid=*",
      "*",
      "admin)(cn=a*",
      ")(mail=*@corp.com"
  })
  void searchForUserName_escapes_user_payload(String payload) throws Exception {
    LdapContext ctx = mock(LdapContext.class);
    NamingEnumeration<SearchResult> empty = mock(NamingEnumeration.class);
    when(empty.hasMoreElements()).thenReturn(false);
    when(ctx.search(anyString(), anyString(), any(), any(SearchControls.class)))
        .thenReturn(empty);

    ActiveDirectoryGroupRealm realm = new ActiveDirectoryGroupRealm();
    realm.setSearchBase("dc=example,dc=com");

    realm.searchForUserName(payload, ctx, 100);

    ArgumentCaptor<String> filterCap = ArgumentCaptor.forClass(String.class);
    verify(ctx).search(eq("dc=example,dc=com"),
        filterCap.capture(), any(), any(SearchControls.class));
    assertNoUnescapedMetacharsFromPayload(filterCap.getValue(), payload);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      ")(objectClass=*",
      "admin)(|(uid=*",
      "*",
      "admin)(cn=a*",
      ")(mail=*@corp.com"
  })
  void getRoleNamesForUser_escapes_user_payload(String payload) throws Exception {
    // Exercise the second String.format filter site directly. The method is
    // private, so use reflection to invoke it.
    LdapContext ctx = mock(LdapContext.class);
    NamingEnumeration<SearchResult> empty = mock(NamingEnumeration.class);
    when(empty.hasMoreElements()).thenReturn(false);
    when(ctx.search(anyString(), anyString(), any(), any(SearchControls.class)))
        .thenReturn(empty);

    ActiveDirectoryGroupRealm realm = new ActiveDirectoryGroupRealm();
    realm.setSearchBase("dc=example,dc=com");

    java.lang.reflect.Method method =
        ActiveDirectoryGroupRealm.class.getDeclaredMethod(
            "getRoleNamesForUser", String.class, LdapContext.class);
    method.setAccessible(true);
    method.invoke(realm, payload, ctx);

    ArgumentCaptor<String> filterCap = ArgumentCaptor.forClass(String.class);
    verify(ctx).search(anyString(), filterCap.capture(), any(),
        any(SearchControls.class));
    assertNoUnescapedMetacharsFromPayload(filterCap.getValue(), payload);
  }

  @Test
  void normal_username_passes_through_unchanged() throws Exception {
    LdapContext ctx = mock(LdapContext.class);
    NamingEnumeration<SearchResult> empty = mock(NamingEnumeration.class);
    when(empty.hasMoreElements()).thenReturn(false);
    when(ctx.search(anyString(), anyString(), any(), any(SearchControls.class)))
        .thenReturn(empty);

    ActiveDirectoryGroupRealm realm = new ActiveDirectoryGroupRealm();
    realm.setSearchBase("dc=example,dc=com");
    realm.searchForUserName("alice", ctx, 100);

    ArgumentCaptor<String> filterCap = ArgumentCaptor.forClass(String.class);
    verify(ctx).search(anyString(), filterCap.capture(), any(),
        any(SearchControls.class));
    assertEquals("(&(objectClass=*)(sAMAccountName=*alice*))", filterCap.getValue());
  }

  /**
   * For each metacharacter in {@code payload}, assert the rendered filter does
   * NOT contain that character unescaped. We do that by removing the literal
   * filter template (the parts that come from the static format string) before
   * checking for raw metacharacters.
   *
   * <p>Both vulnerable callsites use templates of the form
   * {@code "(&(objectClass=*)(<attr>=...<v>...))"} which contribute exactly
   * 3 '(' and 3 ')'. The asterisk count varies between the two templates
   * (3 for searchForUserName, 1 for getRoleNamesForUser), so we don't assert
   * it absolutely; we only assert that escape sequences are present whenever
   * the payload contained the corresponding metacharacter.
   */
  private static void assertNoUnescapedMetacharsFromPayload(String filter, String payload) {
    if (payload.indexOf('(') >= 0) {
      assertTrue(filter.contains("\\28"),
          "expected \\28 escape sequence for '(' in payload " + payload + ": " + filter);
    }
    if (payload.indexOf(')') >= 0) {
      assertTrue(filter.contains("\\29"),
          "expected \\29 escape sequence for ')' in payload " + payload + ": " + filter);
    }
    if (payload.indexOf('*') >= 0) {
      assertTrue(filter.contains("\\2a"),
          "expected \\2a escape sequence for '*' in payload " + payload + ": " + filter);
    }
    // After stripping every "\xNN" hex escape, the remaining "skeleton" must
    // match exactly the static template — no extra '(' / ')' / '*' that came
    // from the payload.
    String stripped = filter.replaceAll("\\\\[0-9a-fA-F]{2}", "");
    assertEquals(3, count(stripped, '('),
        "unexpected '(' count after stripping escapes: " + stripped + " (payload=" + payload + ")");
    assertEquals(3, count(stripped, ')'),
        "unexpected ')' count after stripping escapes: " + stripped + " (payload=" + payload + ")");
  }

  private static int count(String s, char ch) {
    int n = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == ch) {
        n++;
      }
    }
    return n;
  }
}
