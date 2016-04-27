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

package org.apache.zeppelin.credential;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.zeppelin.conf.ZeppelinConfiguration;

/**
 * Class defining credentials for data source authorization
 */
public class Credentials {
  private static final Logger LOG = LoggerFactory.getLogger(Credentials.class);

  private static Credentials credentials = null;
  private Map<String, UserCredentials> credentialsMap;

  private Credentials() {
    credentialsMap = new HashMap<>();
  }

  public static synchronized Credentials getCredentials() {
    if (credentials == null) {
      credentials = new Credentials();
    }
    return credentials;
  }

  public UserCredentials getUserCredentials(String username) {
    UserCredentials uc = credentialsMap.get(username);
    if (uc == null) {
      uc = new UserCredentials();
    }
    return uc;
  }

  public void putUserCredentials(String username, UserCredentials uc) throws IOException {
    credentialsMap.put(username, uc);
  }

}
