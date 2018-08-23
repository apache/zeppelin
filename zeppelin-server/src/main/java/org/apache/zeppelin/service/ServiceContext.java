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

import java.util.Set;
import org.apache.zeppelin.user.AuthenticationInfo;

/** Context info for Service call */
public class ServiceContext {

  private AuthenticationInfo autheInfo;
  private Set<String> userAndRoles;

  public ServiceContext(AuthenticationInfo authInfo, Set<String> userAndRoles) {
    this.autheInfo = authInfo;
    this.userAndRoles = userAndRoles;
  }

  public AuthenticationInfo getAutheInfo() {
    return autheInfo;
  }

  public Set<String> getUserAndRoles() {
    return userAndRoles;
  }
}
