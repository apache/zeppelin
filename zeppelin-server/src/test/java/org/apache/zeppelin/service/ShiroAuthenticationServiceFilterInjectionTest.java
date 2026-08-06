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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests verifying that the search text supplied to
 * {@code GET /api/security/userlist/{searchText}} cannot inject LDAP filter metacharacters into
 * the filters that {@link ShiroAuthenticationService} builds for the LDAP realms. The rendered
 * filter must never contain unescaped {@code (}, {@code )} or {@code *} characters that
 * originated in the search text.
 */
class ShiroAuthenticationServiceFilterInjectionTest {

  private static final String USER_ATTRIBUTE = "uid";
  private static final String USER_OBJECT_CLASS = "person";

  // "(uid=*%s*)" contributes 1 '(', 1 ')' and the 2 wildcards Zeppelin adds itself.
  private static final int DEFAULT_LDAP_OPEN_PARENS = 1;
  private static final int DEFAULT_LDAP_CLOSE_PARENS = 1;
  private static final int DEFAULT_LDAP_ASTERISKS = 2;

  // "(&(objectclass=person)(uid=*%s*))" contributes 3 '(', 3 ')' and the same 2 wildcards.
  private static final int LDAP_REALM_OPEN_PARENS = 3;
  private static final int LDAP_REALM_CLOSE_PARENS = 3;
  private static final int LDAP_REALM_ASTERISKS = 2;

  static Stream<String> injectionPayloads() {
    return Stream.of(
        ")(uid=*",
        "admin)(|(uid=*",
        "*",
        "admin)(cn=a*",
        ")(mail=*@corp.com",
        "alice)(userPassword=*",
        "alice\\",
        "alice\\2a",
        "alice\\29\\28uid=\\2a",
        "\\",
        "\0");
  }

  @ParameterizedTest
  @MethodSource("injectionPayloads")
  void defaultLdapRealmFilterNeutralizesPayload(String payload) {
    String rendered = ShiroAuthenticationService.buildUserSearchFilter(USER_ATTRIBUTE, payload);

    assertMetacharacterCounts(rendered, payload,
        DEFAULT_LDAP_OPEN_PARENS, DEFAULT_LDAP_CLOSE_PARENS, DEFAULT_LDAP_ASTERISKS);
  }

  @ParameterizedTest
  @MethodSource("injectionPayloads")
  void ldapRealmFilterNeutralizesPayload(String payload) {
    String rendered = ShiroAuthenticationService.buildUserSearchFilterWithObjectClass(
        USER_OBJECT_CLASS, USER_ATTRIBUTE, payload);

    assertMetacharacterCounts(rendered, payload,
        LDAP_REALM_OPEN_PARENS, LDAP_REALM_CLOSE_PARENS, LDAP_REALM_ASTERISKS);
  }

  @Test
  void normalSearchTextKeepsSubstringMatching() {
    assertEquals("(uid=*alice*)",
        ShiroAuthenticationService.buildUserSearchFilter(USER_ATTRIBUTE, "alice"));
    assertEquals("(&(objectclass=person)(uid=*alice*))",
        ShiroAuthenticationService.buildUserSearchFilterWithObjectClass(
            USER_OBJECT_CLASS, USER_ATTRIBUTE, "alice"));
  }

  @Test
  void asteriskInSearchTextBecomesLiteral() {
    // The wildcards Zeppelin adds stay wildcards, the one typed by the user does not.
    assertEquals("(uid=*a\\2ab*)",
        ShiroAuthenticationService.buildUserSearchFilter(USER_ATTRIBUTE, "a*b"));
    assertEquals("(&(objectclass=person)(uid=*a\\2ab*))",
        ShiroAuthenticationService.buildUserSearchFilterWithObjectClass(
            USER_OBJECT_CLASS, USER_ATTRIBUTE, "a*b"));
  }

  @Test
  void emptySearchTextKeepsExistingBehaviour() {
    assertEquals("(uid=**)",
        ShiroAuthenticationService.buildUserSearchFilter(USER_ATTRIBUTE, ""));
    assertEquals("(&(objectclass=person)(uid=**))",
        ShiroAuthenticationService.buildUserSearchFilterWithObjectClass(
            USER_OBJECT_CLASS, USER_ATTRIBUTE, ""));
  }

  @Test
  void configuredAttributeNamesAreEscapedAsWell() {
    assertEquals("(&(objectclass=per\\29son)(u\\28id=*alice*))",
        ShiroAuthenticationService.buildUserSearchFilterWithObjectClass("per)son", "u(id",
            "alice"));
  }

  @Test
  void backslashAndNulInSearchTextAreEscaped() {
    assertEquals("(uid=*alice\\5c*)",
        ShiroAuthenticationService.buildUserSearchFilter(USER_ATTRIBUTE, "alice\\"));
    assertEquals("(uid=*alice\\00*)",
        ShiroAuthenticationService.buildUserSearchFilter(USER_ATTRIBUTE, "alice\0"));
    assertEquals("(&(objectclass=person)(uid=*alice\\5c\\00*))",
        ShiroAuthenticationService.buildUserSearchFilterWithObjectClass(
            USER_OBJECT_CLASS, USER_ATTRIBUTE, "alice\\\0"));
  }

  private static void assertMetacharacterCounts(String rendered, String payload,
      int expectedOpenParens, int expectedCloseParens, int expectedAsterisks) {
    assertEquals(expectedOpenParens, count(rendered, '('),
        "extra unescaped '(' from payload: " + rendered);
    assertEquals(expectedCloseParens, count(rendered, ')'),
        "extra unescaped ')' from payload: " + rendered);
    assertEquals(expectedAsterisks, count(rendered, '*'),
        "extra unescaped '*' from payload: " + rendered);

    if (payload.indexOf('(') >= 0) {
      assertTrue(rendered.contains("\\28"), "missing \\28 in: " + rendered);
    }
    if (payload.indexOf(')') >= 0) {
      assertTrue(rendered.contains("\\29"), "missing \\29 in: " + rendered);
    }
    if (payload.indexOf('*') >= 0) {
      assertTrue(rendered.contains("\\2a"), "missing \\2a in: " + rendered);
    }
    if (payload.indexOf('\\') >= 0) {
      assertTrue(rendered.contains("\\5c"), "missing \\5c in: " + rendered);
    }
    if (payload.indexOf('\0') >= 0) {
      assertTrue(rendered.contains("\\00"), "missing \\00 in: " + rendered);
    }
  }

  /**
   * Counts occurrences of {@code ch} in the rendered filter. {@code LdapFilterEncoder} replaces
   * every metacharacter with a hex escape such as {@code \2a}, so a metacharacter that is still
   * present as itself is by definition an unescaped one.
   */
  private static int count(String s, char ch) {
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == ch) {
        count++;
      }
    }
    return count;
  }
}
