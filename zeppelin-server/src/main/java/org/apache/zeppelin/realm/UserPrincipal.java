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

import java.security.Principal;
import org.jvnet.libpam.UnixUser;

/** A {@code java.security.Principal} implememtation for use with Shiro {@code PamRealm}. */
public class UserPrincipal implements Principal {
  private final UnixUser userName;

  public UserPrincipal(UnixUser userName) {
    this.userName = userName;
  }

  @Override
  public String getName() {
    return userName.getUserName();
  }

  public UnixUser getUnixUser() {
    return userName;
  }

  @Override
  public String toString() {
    return String.valueOf(userName.getUserName());
  }
}
