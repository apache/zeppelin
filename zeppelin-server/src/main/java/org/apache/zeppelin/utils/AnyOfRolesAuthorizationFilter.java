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
package org.apache.zeppelin.utils;

import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authz.RolesAuthorizationFilter;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * Allows access if current user has at least one role of the specified list.
 * <p>
 * Basically, it's the same as {@link RolesAuthorizationFilter} but using {@literal OR} instead
 * of {@literal AND} on the specified roles.
 */
public class AnyOfRolesAuthorizationFilter extends RolesAuthorizationFilter {

  @Override
  public boolean isAccessAllowed(ServletRequest request, ServletResponse response,
                                 Object mappedValue) throws IOException {

    final Subject subject = getSubject(request, response);
    final String[] rolesArray = (String[]) mappedValue;

    if (rolesArray == null || rolesArray.length == 0) {
      //no roles specified, so nothing to check - allow access.
      return true;
    }

    for (String roleName : rolesArray) {
      if (subject.hasRole(roleName)) {
        return true;
      }
    }
    return false;
  }
}
