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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.Test;

class KerberosRealmTest {

  @Test
  void sameAuthenticatedPrincipalDoesNotRotateTheSession() {
    KerberosRealm realm = new KerberosRealm();
    Subject subject = mock(Subject.class);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getPrincipal()).thenReturn("user@example.com");
    KerberosToken token = new KerberosToken("user@example.com", "signed-token");

    realm.loginIfNecessary(subject, token);

    verify(subject, never()).login(token);
  }

  @Test
  void unauthenticatedSubjectLogsIn() {
    KerberosRealm realm = new KerberosRealm();
    Subject subject = mock(Subject.class);
    KerberosToken token = new KerberosToken("user@example.com", "signed-token");

    realm.loginIfNecessary(subject, token);

    verify(subject).login(token);
  }

  @Test
  void differentAuthenticatedPrincipalLogsIn() {
    KerberosRealm realm = new KerberosRealm();
    Subject subject = mock(Subject.class);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getPrincipal()).thenReturn("other@example.com");
    KerberosToken token = new KerberosToken("user@example.com", "signed-token");

    realm.loginIfNecessary(subject, token);

    verify(subject).login(token);
  }
}
