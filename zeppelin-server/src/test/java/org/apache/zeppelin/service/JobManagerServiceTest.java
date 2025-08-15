/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.service.exception.JobManagerForbiddenException;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JobManagerServiceTest {

  private ZeppelinConfiguration zConf;
  private Notebook mockNotebook;
  private AuthorizationService mockAuthorizationService;
  private JobManagerService jobManagerService;
  private ServiceContext serviceContext;

  @BeforeEach
  public void setUp() {
    zConf = mock(ZeppelinConfiguration.class);
    mockNotebook = mock(Notebook.class);
    mockAuthorizationService = mock(AuthorizationService.class);
    jobManagerService = new JobManagerService(mockNotebook, mockAuthorizationService, zConf);
    serviceContext = new ServiceContext(new AuthenticationInfo("test-user"), null);
  }

  @Test
  void shouldThrowForbiddenException_whenJobManagerIsDisabled() {
    when(zConf.isJobManagerEnabled()).thenReturn(false);

    assertThrows(JobManagerForbiddenException.class, () -> {
      jobManagerService.getNoteJobInfo("some_note_id", serviceContext, new SimpleServiceCallback<>());
    });

    assertThrows(JobManagerForbiddenException.class, () -> {
      jobManagerService.getNoteJobInfoByUnixTime(0, serviceContext, new SimpleServiceCallback<>());
    });

    assertThrows(JobManagerForbiddenException.class, () -> {
      jobManagerService.removeNoteJobInfo("some_note_id", serviceContext, new SimpleServiceCallback<>());
    });
  }

}
