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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;

class AuthenticatedIdentityTest {

  @Test
  void copiesRolesAndExposesAnImmutableSnapshot() {
    Set<String> roles = new HashSet<>();
    roles.add("reader");

    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user", roles, true, "session-id");
    roles.add("admin");

    assertEquals(Set.of("reader"), identity.getRoles());
    assertThrows(UnsupportedOperationException.class, () -> identity.getRoles().add("writer"));
    assertEquals("session-id", identity.getSessionId().orElseThrow());
    assertTrue(identity.isAuthenticated());
  }

  @Test
  void providesAnAnonymousIdentityWithoutASession() {
    AuthenticatedIdentity identity = AuthenticatedIdentity.anonymous();

    assertEquals(AuthenticatedIdentity.ANONYMOUS_PRINCIPAL, identity.getPrincipal());
    assertEquals(Set.of(), identity.getRoles());
    assertFalse(identity.isAuthenticated());
    assertTrue(identity.getSessionId().isEmpty());
  }
}
