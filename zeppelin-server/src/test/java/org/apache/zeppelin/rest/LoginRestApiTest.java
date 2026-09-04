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
package org.apache.zeppelin.rest;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class LoginRestApiTest {

  @Test
  void reloginLogsOutBeforeLoginAndCreatesTheFinalSessionAfterLogin() {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    when(authenticationService.getPrincipal()).thenReturn("user1");
    when(authenticationService.getAssociatedRoles()).thenReturn(Set.of("role1"));
    AuthorizationService authorizationService = mock(AuthorizationService.class);
    LoginRestApi loginRestApi = new LoginRestApi(
        mock(ZeppelinConfiguration.class),
        authenticationService,
        authorizationService);
    Subject subject = mock(Subject.class);
    UsernamePasswordToken token = new UsernamePasswordToken("user1", "password");

    loginRestApi.proceedToLogin(subject, token);

    InOrder order = inOrder(subject);
    order.verify(subject).logout();
    order.verify(subject).login(token);
    order.verify(subject).getSession(true);
    org.mockito.Mockito.verify(authorizationService).setRoles("user1", Set.of("role1"));
  }
}
