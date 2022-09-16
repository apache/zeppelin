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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class CredentialsInfoSavingOldGenerator {


  public static Map<String, Map<String, UsernamePasswords>> generateOldStructure(Credentials credentials) {
    Map<String, Map<String, UsernamePasswords>> credentialsMap = new HashMap<>();
    for (Entry<String, Credential> cred : credentials.entrySet()) {
      String principal = cred.getValue().getOwners().stream().findFirst().orElse("Unknown");
      String entity = cred.getKey();
      String username = cred.getValue().getUsername();
      String password = cred.getValue().getPassword();
      Map<String, UsernamePasswords> principalMap = credentialsMap.getOrDefault(principal, new HashMap<>());
      UsernamePasswords up = principalMap.getOrDefault(CredentialsInfoSavingOld.USER_CREDENTIALS, new UsernamePasswords());
      up.putUsernamePassword(entity, new UsernamePassword(username, password));
      principalMap.put(CredentialsInfoSavingOld.USER_CREDENTIALS, up);
      credentialsMap.put(principal, principalMap);
    }
    return credentialsMap;
  }
}
