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
package org.apache.zeppelin.realm.jwt;

import java.util.Collection;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.filter.authc.FormAuthenticationFilter;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.ShiroHttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Created for org.apache.zeppelin.server. */
public class KnoxAuthenticationFilter extends FormAuthenticationFilter {
  private static final Logger LOGGER = LoggerFactory.getLogger(KnoxAuthenticationFilter.class);

  protected boolean isAccessAllowed(
      ServletRequest request, ServletResponse response, Object mappedValue) {
    // Check with existing shiro authentication logic
    // https://github.com/apache/shiro/blob/shiro-root-1.3.2/web/src/main/java/org/apache/shiro/
    // web/filter/authc/AuthenticatingFilter.java#L123-L124
    boolean accessAllowed =
        super.isAccessAllowed(request, response, mappedValue)
            || !isLoginRequest(request, response) && isPermissive(mappedValue);

    if (accessAllowed) {
      accessAllowed = false;
      KnoxJwtRealm knoxJwtRealm = null;
      // TODO(jl): Is this logic really useful?
      DefaultWebSecurityManager defaultWebSecurityManager;
      String key = ThreadContext.SECURITY_MANAGER_KEY;
      defaultWebSecurityManager = (DefaultWebSecurityManager) ThreadContext.get(key);
      Collection<Realm> realms = defaultWebSecurityManager.getRealms();
      for (Object realm : realms) {
        if (realm instanceof KnoxJwtRealm) {
          knoxJwtRealm = (KnoxJwtRealm) realm;
          break;
        }
      }
      if (null != knoxJwtRealm) {
        for (Cookie cookie : ((ShiroHttpServletRequest) request).getCookies()) {
          if (cookie.getName().equals(knoxJwtRealm.getCookieName())) {
            if (knoxJwtRealm.validateToken(cookie.getValue())) {
              accessAllowed = true;
            }
            break;
          }
        }
      } else {
        LOGGER.error(
            "Looks like this filter is enabled without enabling KnoxJwtRealm, please refer"
                + " to https://zeppelin.apache.org/docs/latest/security/shiroauthentication.html"
                + "#knox-sso");
      }
    }
    return accessAllowed;
  }
}
