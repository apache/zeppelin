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

/**
 * User Credentials POJO
 *
 * Key: Credential entity
 *
 * Value: credentials
 */
public class UsernamePasswords extends HashMap<String, UsernamePassword> {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public UsernamePassword getUsernamePassword(String entity) {
    return get(entity);
  }

  /**
   * Wrapper method for {@link HashMap#remove(Object)}
   */
  public UsernamePassword removeUsernamePassword(String entity) {
    return remove(entity);
  }

  /**
   * Wrapper method for {@link HashMap#put(Object, Object)}
   */
  public UsernamePassword putUsernamePassword(String entity, UsernamePassword up) {
    return put(entity, up);
  }

  /**
   * Wrapper method for {@link HashMap#containsKey(Object)}
   */
  public boolean existUsernamePassword(String entity) {
    return containsKey(entity);
  }

}
