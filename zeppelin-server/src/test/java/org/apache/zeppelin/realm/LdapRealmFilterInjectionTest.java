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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * End-to-end style tests verifying that user-controlled values flowing through
 * {@link LdapRealm#expandFilterTemplate(String, String)} cannot inject LDAP
 * filter metacharacters. Each known PoC payload from the security report must
 * leave the rendered filter free of unescaped {@code (}, {@code )}, or
 * {@code *} characters that originated in the user input.
 */
class LdapRealmFilterInjectionTest {

  // The default member substitution token used by Zeppelin's LdapRealm.
  private static final String TEMPLATE = "(&(objectclass=person)(uid={0}))";

  // The template "(&(objectclass=person)(uid={0}))" by itself contributes
  // 3 '(' and 3 ')' and 0 '*'. After payload substitution, those counts must
  // remain unchanged — any additional unescaped metacharacter must have leaked
  // from the payload.
  private static final int TEMPLATE_OPEN_PARENS = 3;
  private static final int TEMPLATE_CLOSE_PARENS = 3;
  private static final int TEMPLATE_ASTERISKS = 0;

  @ParameterizedTest
  @ValueSource(strings = {
      ")(uid=*",
      "admin)(|(uid=*",
      "*",
      "admin)(cn=a*",
      ")(mail=*@corp.com",
      "alice)(userPassword=*"
  })
  void poc_payloads_are_neutralized_in_rendered_filter(String payload) {
    String rendered = LdapRealm.expandFilterTemplate(TEMPLATE, payload);

    // After substitution the filter must keep exactly the parens and asterisks
    // that came from the template — no extra ones from the payload.
    assertEquals(TEMPLATE_OPEN_PARENS, countUnescaped(rendered, '('),
        "extra unescaped '(' from payload: " + rendered);
    assertEquals(TEMPLATE_CLOSE_PARENS, countUnescaped(rendered, ')'),
        "extra unescaped ')' from payload: " + rendered);
    assertEquals(TEMPLATE_ASTERISKS, countUnescaped(rendered, '*'),
        "unescaped '*' from payload: " + rendered);

    // Sanity: the escape sequences for the metacharacters must appear when the
    // payload contained them.
    if (payload.indexOf('(') >= 0) {
      assertTrue(rendered.contains("\\28"), "missing \\28 in: " + rendered);
    }
    if (payload.indexOf(')') >= 0) {
      assertTrue(rendered.contains("\\29"), "missing \\29 in: " + rendered);
    }
    if (payload.indexOf('*') >= 0) {
      assertTrue(rendered.contains("\\2a"), "missing \\2a in: " + rendered);
    }
  }

  @Test
  void normal_username_passes_through_unchanged() {
    String rendered = LdapRealm.expandFilterTemplate(TEMPLATE, "alice");
    assertEquals("(&(objectclass=person)(uid=alice))", rendered);
  }

  @Test
  void null_input_yields_empty_substitution() {
    String rendered = LdapRealm.expandFilterTemplate(TEMPLATE, null);
    assertEquals("(&(objectclass=person)(uid=))", rendered);
  }

  @Test
  void escape_output_for_literal_brace_input_is_substituted_once() {
    // Sanity check that String.replace performs a single pass — a literal "{0}"
    // in user input is substituted by the first match only because the
    // substitution-token characters (`{`, `0`, `}`) are not LDAP filter
    // metacharacters and therefore are not transformed by the escape function.
    String rendered = LdapRealm.expandFilterTemplate(TEMPLATE, "{0}");
    // The first {0} placeholder is replaced by the input "{0}" — net result
    // is that one literal "{0}" appears at the substitution site.
    assertEquals("(&(objectclass=person)(uid={0}))", rendered);
  }

  /**
   * Counts occurrences of {@code ch} that are NOT preceded by a single
   * backslash. The simple LDAP filter escape sequences in this codebase emit
   * exactly one backslash before each metacharacter, so this is a sufficient
   * heuristic for asserting "no unescaped metacharacter from user input".
   */
  private static int countUnescaped(String s, char ch) {
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) != ch) {
        continue;
      }
      // Treat {@code \xNN} hex escapes (e.g. "\\28") as escaped metacharacters
      // and don't count them. The raw template literal characters that are
      // part of e.g. "(&(...)" are counted, but those originate from the
      // template, not the user-supplied payload.
      if (i >= 1 && s.charAt(i - 1) == '\\') {
        continue;
      }
      count++;
    }
    return count;
  }
}
