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

import org.apache.shiro.web.filter.authc.FormAuthenticationFilter;
import org.apache.shiro.web.servlet.ShiroHttpServletRequest;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;

/**
 * Created for org.apache.zeppelin.server
 */
public class KnoxAuthenticationFilter extends FormAuthenticationFilter {

  private String cookieName = "hadoop-jwt";

  protected boolean isAccessAllowed(ServletRequest request,
                                    ServletResponse response, Object mappedValue) {

    //Check with existing shiro authentication logic
    //https://github.com/apache/shiro/blob/shiro-root-1.3.2/web/src/main/java/org/apache/shiro/
    // web/filter/authc/AuthenticatingFilter.java#L123-L124
    Boolean accessAllowed = super.isAccessAllowed(request, response, mappedValue) ||
      !isLoginRequest(request, response) && isPermissive(mappedValue);

    if (accessAllowed) {
      accessAllowed = false;
      for (Cookie cookie : ((ShiroHttpServletRequest) request).getCookies()) {
        if (cookie.getName().equals(cookieName)) {
          accessAllowed = true;
          break;
        }
      }
    }
    return accessAllowed;
  }

  public String getCookieName() {
    return cookieName;
  }

  public void setCookieName(String cookieName) {
    this.cookieName = cookieName;
  }
}
