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

package org.apache.zeppelin.web;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.server.CorsFilter;
import org.apache.zeppelin.utils.SecurityUtils;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import javax.inject.Inject;
import javax.servlet.DispatcherType;
import java.io.File;
import java.util.EnumSet;

/**
 * Default implementation of the actions to be taken
 * by Zeppelin to secure the Web channel.
 */
public class DefaultWebSecurity implements WebSecurity {

  @Inject
  private ZeppelinConfiguration conf;

  @Override
  public void addCorFilter(WebAppContext webApp) {

    webApp.addFilter(new FilterHolder(CorsFilter.class), "/*",
            EnumSet.allOf(DispatcherType.class));

  }

  @Override
  public void addSecurityFilter(WebAppContext webApp) {

    webApp.setInitParameter("shiroConfigLocations",
            new File(conf.getShiroPath()).toURI().toString());

    SecurityUtils.initSecurityManager(conf.getShiroPath());
    webApp.addFilter(org.apache.shiro.web.servlet.ShiroFilter.class, "/api/*",
            EnumSet.allOf(DispatcherType.class));

    webApp.addEventListener(new org.apache.shiro.web.env.EnvironmentLoaderListener());

  }

}
