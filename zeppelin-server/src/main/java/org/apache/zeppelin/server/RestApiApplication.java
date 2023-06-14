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

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.apache.zeppelin.rest.AdminRestApi;
import org.apache.zeppelin.rest.ClusterRestApi;
import org.apache.zeppelin.rest.ConfigurationsRestApi;
import org.apache.zeppelin.rest.CredentialRestApi;
import org.apache.zeppelin.rest.HeliumRestApi;
import org.apache.zeppelin.rest.InterpreterRestApi;
import org.apache.zeppelin.rest.LoginRestApi;
import org.apache.zeppelin.rest.NotebookRepoRestApi;
import org.apache.zeppelin.rest.NotebookRestApi;
import org.apache.zeppelin.rest.SecurityRestApi;
import org.apache.zeppelin.rest.SessionRestApi;
import org.apache.zeppelin.rest.ZeppelinRestApi;
import org.apache.zeppelin.rest.exception.WebApplicationExceptionMapper;

public class RestApiApplication extends Application {
  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> s = new HashSet<>();
    s.add(AdminRestApi.class);
    s.add(ClusterRestApi.class);
    s.add(ConfigurationsRestApi.class);
    s.add(CredentialRestApi.class);
    s.add(HeliumRestApi.class);
    s.add(InterpreterRestApi.class);
    s.add(LoginRestApi.class);
    s.add(NotebookRepoRestApi.class);
    s.add(NotebookRestApi.class);
    s.add(SecurityRestApi.class);
    s.add(SessionRestApi.class);
    s.add(ZeppelinRestApi.class);

    // add ExceptionMapper
    s.add(WebApplicationExceptionMapper.class);
    return s;
  }
}
