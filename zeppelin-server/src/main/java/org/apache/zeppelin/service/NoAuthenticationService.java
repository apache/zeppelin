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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import org.apache.shiro.realm.Realm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoAuthenticationService implements AuthenticationService {
  private static Logger logger = LoggerFactory.getLogger(NoAuthenticationService.class);
  private final String ANONYMOUS = "anonymous";

  @Inject
  public NoAuthenticationService() {
    logger.info("NoAuthenticationService is initialized");
  }

  @Override
  public String getPrincipal() {
    return ANONYMOUS;
  }

  @Override
  public Set<String> getAssociatedRoles() {
    return Sets.newHashSet();
  }

  @Override
  public Collection<Realm> getRealmsList() {
    return Collections.emptyList();
  }

  @Override
  public boolean isAuthenticated() {
    return false;
  }

  @Override
  public List<String> getMatchedUsers(String searchText, int numUsersToFetch) {
    return Lists.newArrayList();
  }

  @Override
  public List<String> getMatchedRoles() {
    return Lists.newArrayList();
  }
}
