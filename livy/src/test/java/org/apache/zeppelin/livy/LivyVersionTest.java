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
package org.apache.zeppelin.livy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LivyVersionTest {

  @Test
  void testVersionParsing() {
    LivyVersion v = new LivyVersion("1.6.2");
    assertEquals(10602, v.toNumber());
    assertEquals("1.6.2", v.toString());
  }

  @Test
  void testPreReleaseVersionParsing() {
    LivyVersion v = new LivyVersion("0.6.0-SNAPSHOT");
    assertEquals(600, v.toNumber());
    assertEquals("0.6.0-SNAPSHOT", v.toString());
  }

  @Test
  void testComparisonLogic() {
    LivyVersion v1 = new LivyVersion("0.5.0");
    LivyVersion v2 = new LivyVersion("0.4.0");
    assertTrue(v1.newerThan(v2));
    assertTrue(v2.olderThan(v1));
    assertFalse(v2.newerThan(v1));
    assertFalse(v1.olderThan(v2));
  }

  @Test
  void testInvalidVersionString() {
    LivyVersion v1 = new LivyVersion("invalid");
    assertEquals(99999, v1.toNumber());

    LivyVersion v2 = new LivyVersion(null);
    assertEquals(99999, v2.toNumber());
  }

  @Test
  void isCancelSupported() {
    assertTrue(new LivyVersion("0.3.0").isCancelSupported());
    assertFalse(new LivyVersion("0.2.0").isCancelSupported());
  }

  @Test
  void isSharedSupported() {
    assertTrue(new LivyVersion("0.5.0").isSharedSupported());
    assertFalse(new LivyVersion("0.4.0").isSharedSupported());
  }

  @Test
  void isGetProgressSupported() {
    assertTrue(new LivyVersion("0.4.0").isGetProgressSupported());
    assertFalse(new LivyVersion("0.3.0").isGetProgressSupported());
  }
}

