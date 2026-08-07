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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.apache.zeppelin.user.AuthenticationInfo;

/** Creates transport-neutral service contexts from server-authenticated identities. */
public final class ServiceContextFactory {

  private ServiceContextFactory() {
  }

  public static ServiceContext create(AuthenticatedIdentity identity) {
    Objects.requireNonNull(identity, "identity");

    Set<String> roles = new HashSet<>(identity.getRoles());
    AuthenticationInfo authenticationInfo =
        new AuthenticationInfo(identity.getPrincipal(), roles, null);

    Set<String> userAndRoles = new HashSet<>(roles);
    userAndRoles.add(identity.getPrincipal());
    return new ServiceContext(authenticationInfo, userAndRoles);
  }
}
