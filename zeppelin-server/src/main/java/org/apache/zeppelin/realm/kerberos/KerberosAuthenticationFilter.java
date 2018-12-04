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
package org.apache.zeppelin.realm.kerberos;

import org.apache.hadoop.security.authentication.server.AuthenticationHandler;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.filter.authc.PassThruAuthenticationFilter;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import java.io.IOException;
import java.util.Collection;

/**
 * Created for org.apache.zeppelin.server
 */
public class KerberosAuthenticationFilter extends PassThruAuthenticationFilter {

  private static final Logger LOG = LoggerFactory.getLogger(KerberosAuthenticationFilter.class);

  @Override
  protected void saveRequestAndRedirectToLogin(ServletRequest request, ServletResponse response) {
    // We don't want to redirect request to loginUrl here
  }

  /**
   * If the request has a valid authentication token it allows the request to continue to
   * the target resource,
   * otherwise it triggers an authentication sequence using the configured
   * {@link AuthenticationHandler}.
   *
   * @param request     the request object.
   * @param response    the response object.
   * @param filterChain the filter chain object.
   * @throws IOException      thrown if an IO error occurred.
   * @throws ServletException thrown if a processing error occurred.
   */
  @Override
  public void doFilterInternal(ServletRequest request,
                               ServletResponse response,
                               FilterChain filterChain)
      throws IOException, ServletException {
    KerberosRealm kerberosRealm = null;
    DefaultWebSecurityManager defaultWebSecurityManager;
    String key = ThreadContext.SECURITY_MANAGER_KEY;
    defaultWebSecurityManager = (DefaultWebSecurityManager) ThreadContext.get(key);
    Collection<Realm> realms = defaultWebSecurityManager.getRealms();
    for (Object realm : realms) {
      if (realm instanceof KerberosRealm) {
        kerberosRealm = (KerberosRealm) realm;
        break;
      }
    }
    if (kerberosRealm != null) {
      kerberosRealm.doKerberosAuth(request, response, filterChain);
    } else {
      LOG.error("Looks like this filter is enabled without enabling KerberosRealm, please refer"
          + " to https://zeppelin.apache.org/docs/latest/security/shiroauthentication.html"
          + "#kerberos-auth");
    }
  }
}
