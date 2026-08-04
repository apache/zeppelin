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

package org.apache.zeppelin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class IdHashesTest {

  /**
   * The characters generated IDs are expected to be built from: digits 1-9 and A-Z without
   * I, L and O. Those three letters and the digit 0 are left out so that an ID stays
   * unambiguous when a person reads it off a URL.
   *
   * <p>Spelled out here rather than read back from IdHashes so that changing the dictionary
   * has to be a deliberate act that updates this test too.
   */
  private static final String EXPECTED_CHARACTERS = "123456789ABCDEFGHJKMNPQRSTUVWXYZ";

  /**
   * IDs are derived from {@code currentTimeMillis() + SecureRandom.nextInt()}, so uniqueness
   * can only be asserted probabilistically. The random term spans 2^32 values, which puts the
   * chance of a collision within one sample of this size on the order of 1e-4.
   */
  private static final int SAMPLE_SIZE = 1000;

  @Test
  void generatedIdsContainOnlyDictionaryCharacters() {
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      String id = IdHashes.generateId();
      for (char c : id.toCharArray()) {
        assertTrue(EXPECTED_CHARACTERS.indexOf(c) >= 0,
            "generated id '" + id + "' contains '" + c + "', which is not in the dictionary");
      }
    }
  }

  @Test
  void generatedIdsAreNeverEmpty() {
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      assertFalse(IdHashes.generateId().isEmpty(), "generateId() returned an empty id");
    }
  }

  @Test
  void generatedIdsAreDistinctAcrossManyCalls() {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      ids.add(IdHashes.generateId());
    }
    assertEquals(SAMPLE_SIZE, ids.size(), "generateId() produced duplicate ids");
  }
}
