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
package org.apache.zeppelin.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.shiro.config.Ini;
import org.apache.shiro.web.env.IniWebEnvironment;
import org.apache.shiro.web.env.WebEnvironment;
import org.apache.shiro.web.filter.mgt.DefaultFilterChainManager;
import org.apache.shiro.web.filter.mgt.FilterChainManager;
import org.apache.shiro.web.filter.mgt.FilterChainResolver;
import org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.apache.shiro.web.util.WebUtils;

/** Shiro filter that rejects requests not covered by an explicit {@code [urls]} rule. */
public class FailClosedShiroFilter extends ShiroFilter {

  private static final String NO_MATCHING_CHAIN = "No matching Shiro URL rule";

  @Override
  public void init() throws Exception {
    super.init();
    WebEnvironment environment = WebUtils.getRequiredWebEnvironment(getServletContext());
    if (environment instanceof IniWebEnvironment) {
      removeImplicitDefaultChain(
          ((IniWebEnvironment) environment).getIni(), getFilterChainResolver());
    }
  }

  static void removeImplicitDefaultChain(Ini ini, FilterChainResolver resolver) {
    Ini.Section urls = ini == null ? null : ini.getSection("urls");
    if (urls != null && urls.containsKey("/**")) {
      return;
    }
    if (!(resolver instanceof PathMatchingFilterChainResolver)) {
      return;
    }

    FilterChainManager manager =
        ((PathMatchingFilterChainResolver) resolver).getFilterChainManager();
    if (manager instanceof DefaultFilterChainManager) {
      // Shiro adds an implicit /** chain containing only its global invalid-request filter.
      // It is not an operator-configured [urls] rule, so remove it and let this filter reject
      // otherwise unmatched requests.
      ((DefaultFilterChainManager) manager).getFilterChains().remove("/**");
    }
  }

  @Override
  protected FilterChain getExecutionChain(
      ServletRequest request, ServletResponse response, FilterChain originalChain) {
    FilterChainResolver resolver = getFilterChainResolver();
    FilterChain resolvedChain =
        resolver == null ? null : resolver.getChain(request, response, originalChain);
    if (resolvedChain != null) {
      return resolvedChain;
    }

    return (ignoredRequest, deniedResponse) ->
        ((HttpServletResponse) deniedResponse)
            .sendError(HttpServletResponse.SC_FORBIDDEN, NO_MATCHING_CHAIN);
  }
}
