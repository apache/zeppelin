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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import java.util.Set;
import org.apache.zeppelin.service.AuthenticatedIdentity;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.service.ServiceContext;

class AbstractRestApiTest {

  @Test
  void createsServiceContextFromOneIdentitySnapshot() {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user", Set.of("reader"), true, "session-id");
    when(authenticationService.getAuthenticatedIdentity()).thenReturn(identity);

    TestRestApi restApi = new TestRestApi(authenticationService);
    ServiceContext context = restApi.exposeServiceContext();

    assertEquals("user", context.getAutheInfo().getUser());
    assertEquals(Set.of("reader"), context.getAutheInfo().getRoles());
    assertEquals(Set.of("user", "reader"), context.getUserAndRoles());
    verify(authenticationService).getAuthenticatedIdentity();
    verifyNoMoreInteractions(authenticationService);
  }

  private static class TestRestApi extends AbstractRestApi {

    TestRestApi(AuthenticationService authenticationService) {
      super(authenticationService);
    }

    ServiceContext exposeServiceContext() {
      return getServiceContext();
    }
  }
}
