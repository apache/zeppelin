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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LdapFilterEncoderTest {

  @Test
  void nullInputReturnsNull() {
    assertNull(LdapFilterEncoder.escapeFilterValue(null));
  }

  @Test
  void emptyInputReturnsEmpty() {
    assertEquals("", LdapFilterEncoder.escapeFilterValue(""));
  }

  @Test
  void plainTextIsUnchanged() {
    assertEquals("alice", LdapFilterEncoder.escapeFilterValue("alice"));
    assertEquals("alice@example.com", LdapFilterEncoder.escapeFilterValue("alice@example.com"));
    assertEquals("Alice Smith", LdapFilterEncoder.escapeFilterValue("Alice Smith"));
  }

  @Test
  void parenthesesAreEscaped() {
    assertEquals("\\28alice\\29", LdapFilterEncoder.escapeFilterValue("(alice)"));
  }

  @Test
  void asteriskIsEscaped() {
    assertEquals("\\2a", LdapFilterEncoder.escapeFilterValue("*"));
    assertEquals("foo\\2abar", LdapFilterEncoder.escapeFilterValue("foo*bar"));
  }

  @Test
  void backslashIsEscaped() {
    assertEquals("\\5c", LdapFilterEncoder.escapeFilterValue("\\"));
  }

  @Test
  void nullByteIsEscaped() {
    assertEquals("\\00", LdapFilterEncoder.escapeFilterValue("\0"));
  }

  // The following payloads were demonstrated by the security report and would
  // bypass an RFC 4514 (DN) escape function. Each must be neutralized.

  @Test
  void filterClosingPayloadIsNeutralized() {
    // ")(uid=*" — closes the current filter and injects a wildcard match
    String escaped = LdapFilterEncoder.escapeFilterValue(")(uid=*");
    assertEquals("\\29\\28uid=\\2a", escaped);
  }

  @Test
  void orInjectionPayloadIsNeutralized() {
    // "admin)(|(uid=*" — OR-injection bypassing password check
    String escaped = LdapFilterEncoder.escapeFilterValue("admin)(|(uid=*");
    assertEquals("admin\\29\\28|\\28uid=\\2a", escaped);
  }

  @Test
  void wildcardOnlyPayloadIsNeutralized() {
    // username "*" alone would match all entries without escape
    assertEquals("\\2a", LdapFilterEncoder.escapeFilterValue("*"));
  }

  @Test
  void blindLdapPrefixPayloadIsNeutralized() {
    // "admin)(cn=a*" — used for character-by-character attribute enumeration
    String escaped = LdapFilterEncoder.escapeFilterValue("admin)(cn=a*");
    assertEquals("admin\\29\\28cn=a\\2a", escaped);
  }

  @Test
  void emailEnumerationPayloadIsNeutralized() {
    // ")(mail=*@corp.com" — used to enumerate users in a target email domain
    String escaped = LdapFilterEncoder.escapeFilterValue(")(mail=*@corp.com");
    assertEquals("\\29\\28mail=\\2a@corp.com", escaped);
  }

  @Test
  void mixedSpecialCharactersAreAllEscaped() {
    // every metacharacter at once
    String input = "(*)\\\0";
    assertEquals("\\28\\2a\\29\\5c\\00", LdapFilterEncoder.escapeFilterValue(input));
  }

  @Test
  void rfc4514OnlyMetacharactersAreLeftAlone() {
    // characters that RFC 4514 (DN) escapes but RFC 4515 (filter) does NOT must
    // pass through untouched. This regression-tests the exact gap between the
    // two RFCs that produced the original CVE-2024-31867 incomplete fix.
    assertEquals(",", LdapFilterEncoder.escapeFilterValue(","));
    assertEquals("+", LdapFilterEncoder.escapeFilterValue("+"));
    assertEquals(";", LdapFilterEncoder.escapeFilterValue(";"));
    assertEquals("\"", LdapFilterEncoder.escapeFilterValue("\""));
    assertEquals("<", LdapFilterEncoder.escapeFilterValue("<"));
    assertEquals(">", LdapFilterEncoder.escapeFilterValue(">"));
    assertEquals("=", LdapFilterEncoder.escapeFilterValue("="));
    assertEquals("#", LdapFilterEncoder.escapeFilterValue("#"));
  }
}
