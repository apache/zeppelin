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

package org.apache.zeppelin.notebook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.repo.InMemoryNotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.storage.InMemoryConfigStorage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {
  private ZeppelinConfiguration zConf;
  private AuthorizationService authorizationService;
  private static final String BLANK_ROLE = " ";
  private static final String EMPTY_ROLE = "";

  @BeforeEach
  private void setup() throws IOException {
    zConf = mock(ZeppelinConfiguration.class);
    when(zConf.isNotebookPublic()).thenReturn(false);
    NotebookRepo notebookRepo = new InMemoryNotebookRepo();
    NoteManager noteManager = new NoteManager(notebookRepo, zConf);
    ConfigStorage storage = new InMemoryConfigStorage(zConf);
    authorizationService = new AuthorizationService(noteManager, zConf, storage);
  }

  @Test
  void testDefaultOwners() throws IOException {
    Note testNote = new Note();
    authorizationService.createNoteAuth(testNote.getId(), new AuthenticationInfo("TestUser"));

    // Comma separated with trim
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_OWNER_ROLES)).thenReturn("TestGroup, TestGroup2");
    for (String role : Arrays.asList("TestGroup", "TestGroup2")) {
      assertTrue(authorizationService.isOwner(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertTrue(authorizationService.isRunner(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertTrue(authorizationService.isWriter(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertTrue(authorizationService.isReader(testNote.getId(), new HashSet<>(Arrays.asList(role))));
    }

    // Empty - Blank
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_OWNER_ROLES)).thenReturn(BLANK_ROLE);
    assertFalse(authorizationService.isOwner(testNote.getId(), new HashSet<>(Arrays.asList(BLANK_ROLE))));
    // Empty - Empty
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_OWNER_ROLES)).thenReturn(EMPTY_ROLE);
    assertFalse(authorizationService.isOwner(testNote.getId(), new HashSet<>(Arrays.asList(EMPTY_ROLE))));
    // Empty - null
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_OWNER_ROLES)).thenReturn(null);
    assertTrue(authorizationService.isOwner(testNote.getId(), new HashSet<>(Arrays.asList("TestUser"))));

  }

  @Test
  void testDefaultRunners() throws IOException {
    Note testNote = new Note();
    authorizationService.createNoteAuth(testNote.getId(), new AuthenticationInfo("TestUser"));

    // Comma separated with trim
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_RUNNER_ROLES)).thenReturn("TestGroup, TestGroup2");
    for (String role : Arrays.asList("TestGroup", "TestGroup2")) {
      assertTrue(authorizationService.isRunner(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertTrue(authorizationService.isReader(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertFalse(authorizationService.isOwner(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertFalse(authorizationService.isWriter(testNote.getId(), new HashSet<>(Arrays.asList(role))));
    }

    // Empty - Blank
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_RUNNER_ROLES)).thenReturn(BLANK_ROLE);
    assertFalse(authorizationService.isRunner(testNote.getId(), new HashSet<>(Arrays.asList(BLANK_ROLE))));
    // Empty - Empty
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_RUNNER_ROLES)).thenReturn(EMPTY_ROLE);
    assertFalse(authorizationService.isRunner(testNote.getId(), new HashSet<>(Arrays.asList(EMPTY_ROLE))));
    // Empty - null
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_RUNNER_ROLES)).thenReturn(null);
    assertTrue(authorizationService.isRunner(testNote.getId(), new HashSet<>(Arrays.asList("TestUser"))));
  }

  @Test
  void testDefaultWriters() throws IOException {
    Note testNote = new Note();
    authorizationService.createNoteAuth(testNote.getId(), new AuthenticationInfo("TestUser"));

    // Comma separated with trim
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_WRITER_ROLES)).thenReturn("TestGroup, TestGroup2");
    for (String role : Arrays.asList("TestGroup", "TestGroup2")) {
      assertTrue(authorizationService.isWriter(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertTrue(authorizationService.isRunner(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertTrue(authorizationService.isReader(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertFalse(authorizationService.isOwner(testNote.getId(), new HashSet<>(Arrays.asList(role))));
    }

    // Empty - Blank
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_WRITER_ROLES)).thenReturn(BLANK_ROLE);
    assertFalse(authorizationService.isWriter(testNote.getId(), new HashSet<>(Arrays.asList(BLANK_ROLE))));
    // Empty - Empty
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_WRITER_ROLES)).thenReturn(EMPTY_ROLE);
    assertFalse(authorizationService.isWriter(testNote.getId(), new HashSet<>(Arrays.asList(EMPTY_ROLE))));
    // Empty - null
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_WRITER_ROLES)).thenReturn(null);
    assertTrue(authorizationService.isWriter(testNote.getId(), new HashSet<>(Arrays.asList("TestUser"))));
  }

  @Test
  void testDefaultReaders() throws IOException {
    Note testNote = new Note();
    authorizationService.createNoteAuth(testNote.getId(), new AuthenticationInfo("TestUser"));

    // Comma separated with trim
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_READER_ROLES)).thenReturn("TestGroup, TestGroup2");
    for (String role : Arrays.asList("TestGroup", "TestGroup2")) {
      assertTrue(authorizationService.isReader(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertFalse(authorizationService.isOwner(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertFalse(authorizationService.isRunner(testNote.getId(), new HashSet<>(Arrays.asList(role))));
      assertFalse(authorizationService.isWriter(testNote.getId(), new HashSet<>(Arrays.asList(role))));
    }

    // Empty - Blank
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_READER_ROLES)).thenReturn(BLANK_ROLE);
    assertFalse(authorizationService.isReader(testNote.getId(), new HashSet<>(Arrays.asList(BLANK_ROLE))));
    // Empty - Empty
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_READER_ROLES)).thenReturn(EMPTY_ROLE);
    assertFalse(authorizationService.isReader(testNote.getId(), new HashSet<>(Arrays.asList(EMPTY_ROLE))));
    // Empty - null
    when(zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_READER_ROLES)).thenReturn(null);
    assertTrue(authorizationService.isReader(testNote.getId(), new HashSet<>(Arrays.asList("TestUser"))));
  }
}
