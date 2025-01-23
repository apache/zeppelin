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

package org.apache.zeppelin.user;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Helper class to save credentials
 * Old content of the credential file
 *
 * <pre>
 * {
 *   "credentialsMap": {
 *     "principal_1": {
 *       "userCredentials": {
 *         "test": {
 *           "username": "test",
 *           "password": "test"
 *         }
 *       }
 *     },
 *     "principal_2": {
 *       "userCredentials": {
 *         "FOO": {
 *           "username": "2",
 *           "password": "1"
 *         }
 *       }
 *     }
 *   }
 * }
 * </pre>
 */

public class CredentialsInfoSavingOld {
  // principal -> UserCredentials (entity -> UsernamePassword)
  private final Map<String, Map<String, UsernamePasswords>> credentialsMap;

  public static final String USER_CREDENTIALS = "userCredentials";

  /**
   * This constructor is not actively used by the running code. Only test classes make use of it. See
   * {@link CredentialsInfoSavingOldGenerator}, which creates the old structure.
   *
   * @param credentialsMap
   */
  public CredentialsInfoSavingOld(Map<String, Map<String, UsernamePasswords>> credentialsMap) {
    super();
    this.credentialsMap = credentialsMap;
  }

  public @Nonnull Credentials getCredentialsMap() {
    Credentials creds = new Credentials();
    for (Entry<String, Map<String, UsernamePasswords>> cred : credentialsMap.entrySet()) {
      String owner = cred.getKey();
      Set<String> owners = new HashSet<>();
      owners.add(owner);
      Set<String> readers = new HashSet<>();
      readers.add(owner);
      UsernamePasswords ups = cred.getValue().getOrDefault(USER_CREDENTIALS, new UsernamePasswords());
      for (Entry<String, UsernamePassword> up : ups.entrySet()) {
        Credential newCred = new Credential(up.getValue().getUsername(), up.getValue().getPassword(), readers, owners);
        creds.putCredential(up.getKey(), newCred);
      }
    }
    return creds;
  }
}
