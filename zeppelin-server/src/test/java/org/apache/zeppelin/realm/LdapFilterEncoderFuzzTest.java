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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Random;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Fuzz tests for {@link LdapFilterEncoder#escapeFilterValue}.
 *
 * <p>Uses a deterministic seed ({@code 0xDEADBEEFL}) for reproducibility.
 * Each repeated test iteration generates a random payload and verifies:
 * <ul>
 *   <li>No raw {@code (}, {@code )}, {@code *} in the output (unless preceded by {@code \})</li>
 *   <li>No NUL byte in the output</li>
 *   <li>Every escape sequence uses the exact RFC 4515 hex encoding</li>
 *   <li>Decoding the escaped output produces the original input</li>
 * </ul>
 */
class LdapFilterEncoderFuzzTest {

  // Deterministic seed — guarantees reproducibility across runs.
  private static final Random RANDOM = new Random(0xDEADBEEFL);

  // RFC 4515 metacharacters that must be escaped.
  private static final char[] METACHAR = {'\\', '(', ')', '*', '\0'};

  // Printable ASCII range (0x20–0x7E).
  private static final int ASCII_PRINTABLE_START = 0x20;
  private static final int ASCII_PRINTABLE_END = 0x7E;

  // Korean Hangul syllables: U+AC00 – U+D7A3
  private static final int HANGUL_START = 0xAC00;
  private static final int HANGUL_END = 0xD7A3;

  // Hiragana: U+3041 – U+3096
  private static final int HIRAGANA_START = 0x3041;
  private static final int HIRAGANA_END = 0x3096;

  /**
   * Generates a random payload of length [0, 200] with a weighted mix of
   * ASCII printable characters, RFC 4515 metacharacters, Hangul syllables,
   * and Hiragana characters.
   */
  private static String randomPayload() {
    int length = RANDOM.nextInt(201); // 0 to 200 inclusive
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.appendCodePoint(randomCodePoint());
    }
    return sb.toString();
  }

  /**
   * Returns a random code point from one of four buckets (weighted):
   * <ul>
   *   <li>30% — RFC 4515 metacharacter</li>
   *   <li>50% — ASCII printable</li>
   *   <li>10% — Hangul syllable</li>
   *   <li>10% — Hiragana</li>
   * </ul>
   */
  private static int randomCodePoint() {
    int bucket = RANDOM.nextInt(100);
    if (bucket < 30) {
      // Metachar
      return METACHAR[RANDOM.nextInt(METACHAR.length)];
    } else if (bucket < 80) {
      // ASCII printable
      return ASCII_PRINTABLE_START + RANDOM.nextInt(ASCII_PRINTABLE_END - ASCII_PRINTABLE_START + 1);
    } else if (bucket < 90) {
      // Hangul
      return HANGUL_START + RANDOM.nextInt(HANGUL_END - HANGUL_START + 1);
    } else {
      // Hiragana
      return HIRAGANA_START + RANDOM.nextInt(HIRAGANA_END - HIRAGANA_START + 1);
    }
  }

  /**
   * Decodes an RFC 4515-escaped string back to the original value.
   * This is the inverse of {@code LdapFilterEncoder.escapeFilterValue}.
   */
  private static String decode(String escaped) {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < escaped.length()) {
      char c = escaped.charAt(i);
      if (c == '\\' && i + 2 < escaped.length()) {
        String hex = escaped.substring(i + 1, i + 3);
        int codePoint = Integer.parseInt(hex, 16);
        sb.append((char) codePoint);
        i += 3;
      } else {
        sb.appendCodePoint(c);
        i++;
      }
    }
    return sb.toString();
  }

  /**
   * Verifies that the escaped output contains no raw metacharacters.
   *
   * <p>A raw metacharacter is one that appears in the output without being
   * preceded by a {@code \} escape prefix. We check this by scanning the
   * output character by character: whenever we see {@code \} we skip the
   * next two characters (the hex pair), otherwise the character must not
   * be a metacharacter.
   */
  private static void assertNoRawMetachars(String escaped) {
    int i = 0;
    while (i < escaped.length()) {
      char c = escaped.charAt(i);
      if (c == '\\') {
        // This backslash must be part of an escape sequence — skip 3 chars total.
        i += 3;
      } else {
        // Must not be a raw metacharacter.
        assertFalse(c == '(' || c == ')' || c == '*' || c == '\0',
            () -> "Raw metacharacter '" + (c == '\0' ? "NUL" : c) + "' found in escaped output: " + escaped);
        i++;
      }
    }
  }

  /**
   * Verifies that every escape sequence in the output uses the exact
   * RFC 4515 hex encoding ({@code \5c}, {@code \28}, {@code \29},
   * {@code \2a}, {@code \00}).
   */
  private static void assertValidEscapeSequences(String escaped) {
    int i = 0;
    while (i < escaped.length()) {
      char c = escaped.charAt(i);
      if (c == '\\') {
        // There must be exactly 2 hex digits following.
        assertFalse(i + 2 >= escaped.length(),
            () -> "Escape sequence at end of string is truncated: " + escaped);
        String hex = escaped.substring(i + 1, i + 3).toLowerCase();
        // Only these 5 sequences are valid RFC 4515 escape outputs.
        boolean validHex = hex.equals("5c") || hex.equals("28")
            || hex.equals("29") || hex.equals("2a") || hex.equals("00");
        String finalHex = hex;
        assertFalse(!validHex,
            () -> "Unexpected escape sequence \\" + finalHex + " in: " + escaped);
        i += 3;
      } else {
        i++;
      }
    }
  }

  // -------------------------------------------------------------------------
  // Fuzz test: 1000 random payloads
  // -------------------------------------------------------------------------

  @RepeatedTest(1000)
  void fuzzRandomPayload() {
    String payload = randomPayload();
    String escaped = LdapFilterEncoder.escapeFilterValue(payload);

    // 1. No NUL byte in output.
    assertFalse(escaped.contains("\0"),
        () -> "NUL byte found in escaped output for input: " + payload);

    // 2. No raw ( ) * in output.
    assertNoRawMetachars(escaped);

    // 3. Every escape sequence uses the correct RFC 4515 hex encoding.
    assertValidEscapeSequences(escaped);

    // 4. Round-trip: decode(escape(input)) == input.
    assertEquals(payload, decode(escaped),
        () -> "Round-trip failed for input: " + payload);
  }

  // -------------------------------------------------------------------------
  // Edge-case tests
  // -------------------------------------------------------------------------

  @Test
  void edgeCaseEmptyString() {
    assertEquals("", LdapFilterEncoder.escapeFilterValue(""));
  }

  @Test
  void edgeCaseVeryLongAllMetachars() {
    // 10 000 characters, all metacharacters (cycling through the 5).
    StringBuilder input = new StringBuilder(10_000);
    for (int i = 0; i < 10_000; i++) {
      input.append(METACHAR[i % METACHAR.length]);
    }
    String payload = input.toString();
    String escaped = LdapFilterEncoder.escapeFilterValue(payload);

    assertFalse(escaped.contains("\0"), "NUL byte must not appear in output");
    assertNoRawMetachars(escaped);
    assertValidEscapeSequences(escaped);
    assertEquals(payload, decode(escaped), "Round-trip must hold for 10 000-char metachar-only input");
  }

  @Test
  void edgeCaseHangulOnly() {
    // 200 Hangul syllables — no metacharacters, output must equal input.
    StringBuilder input = new StringBuilder(200);
    Random r = new Random(0xCAFEBABEL);
    for (int i = 0; i < 200; i++) {
      input.appendCodePoint(HANGUL_START + r.nextInt(HANGUL_END - HANGUL_START + 1));
    }
    String payload = input.toString();
    String escaped = LdapFilterEncoder.escapeFilterValue(payload);

    assertEquals(payload, escaped,
        "Hangul-only input should pass through unchanged");
  }

  @Test
  void edgeCaseHangulMixedWithMetachars() {
    // Hangul syllables interleaved with all 5 metacharacters.
    String payload = "가(각)갂*갃\\간\0갅";
    String escaped = LdapFilterEncoder.escapeFilterValue(payload);

    assertFalse(escaped.contains("\0"), "NUL must be escaped");
    assertNoRawMetachars(escaped);
    assertValidEscapeSequences(escaped);
    assertEquals(payload, decode(escaped), "Round-trip must hold for Hangul+metachar mix");
  }

  @Test
  void edgeCaseUnicodeFullwidthMetacharsAreNotEscaped() {
    // Unicode fullwidth equivalents (U+FF08 '(', U+FF09 ')', U+FF0A '*')
    // are NOT RFC 4515 metacharacters and must pass through unchanged.
    // This is intentional and documents the current scope of the fix.
    String fullwidth = "（）＊";
    String escaped = LdapFilterEncoder.escapeFilterValue(fullwidth);
    assertEquals(fullwidth, escaped,
        "Unicode fullwidth metachar equivalents should NOT be escaped (out of RFC 4515 scope)");
  }
}
