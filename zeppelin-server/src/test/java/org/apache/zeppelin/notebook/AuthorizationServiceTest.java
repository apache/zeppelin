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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {

  @Test
  void missingAuthorizationIsDifferentFromAnExistingPublicAcl() throws Exception {
    NoteManager noteManager = mock(NoteManager.class);
    when(noteManager.getNotesInfo()).thenReturn(Collections.emptyMap());
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.isAnonymousAllowed()).thenReturn(true);
    ConfigStorage storage = mock(ConfigStorage.class);
    AuthorizationService service = new AuthorizationService(noteManager, zConf, storage);
    service.createNoteAuth("note-id", AuthenticationInfo.ANONYMOUS);

    assertTrue(service.isReader("note-id", Set.of("user")));
    assertTrue(service.isOwner("note-id", Set.of("user")));
    assertTrue(service.hasReadPermission(Set.of("user"), "note-id"));

    service.removeNoteAuth("note-id");

    assertFalse(service.isReader("note-id", Set.of("user")));
    assertFalse(service.isOwner("note-id", Set.of("user")));
    assertFalse(service.hasReadPermission(Set.of("user"), "note-id"));
    assertFalse(service.isOwner(Set.of("user"), "note-id"));
  }

  @Test
  void replaceAndClearPermissionsPublishCompleteSnapshots() throws Exception {
    NoteManager noteManager = mock(NoteManager.class);
    when(noteManager.getNotesInfo()).thenReturn(Collections.emptyMap());
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    ConfigStorage storage = mock(ConfigStorage.class);
    AuthorizationService service = new AuthorizationService(noteManager, zConf, storage);
    service.createNoteAuth("note-id", new AuthenticationInfo("initial-owner"));

    Set<String> readers = new HashSet<>(Set.of(" reader ", ""));
    Set<String> runners = new HashSet<>(Set.of(" runner "));
    Set<String> writers = new HashSet<>(Set.of(" writer "));
    Set<String> owners = new HashSet<>(Set.of(" owner "));
    service.setPermissions("note-id", readers, runners, writers, owners);

    readers.add("late-reader");
    runners.add("late-runner");
    writers.add("late-writer");
    owners.add("late-owner");
    assertEquals(Set.of("reader"), service.getReaders("note-id"));
    assertEquals(Set.of("runner"), service.getRunners("note-id"));
    assertEquals(Set.of("writer"), service.getWriters("note-id"));
    assertEquals(Set.of("owner"), service.getOwners("note-id"));

    assertThrows(
        NullPointerException.class,
        () ->
            service.setPermissions(
                "note-id",
                Set.of("partial-reader"),
                Set.of("partial-runner"),
                Set.of("partial-writer"),
                null));
    assertEquals(Set.of("reader"), service.getReaders("note-id"));
    assertEquals(Set.of("runner"), service.getRunners("note-id"));
    assertEquals(Set.of("writer"), service.getWriters("note-id"));
    assertEquals(Set.of("owner"), service.getOwners("note-id"));

    service.clearPermission("note-id");

    assertTrue(service.getReaders("note-id").isEmpty());
    assertTrue(service.getRunners("note-id").isEmpty());
    assertTrue(service.getWriters("note-id").isEmpty());
    assertTrue(service.getOwners("note-id").isEmpty());
  }

  @Test
  void effectiveAclAndRoleChangesAdvanceAuthorizationVersion() throws Exception {
    AuthorizationService service = newAuthorizationService();
    service.createNoteAuth("note-id", new AuthenticationInfo("owner"));
    long initialVersion = service.getAuthorizationVersion();

    service.setPermissions(
        "note-id", Set.of("reader"), Set.of("runner"), Set.of("writer"), Set.of("owner"));
    long aclVersion = service.getAuthorizationVersion();
    assertEquals(initialVersion + 1, aclVersion);

    service.setPermissions(
        "note-id", Set.of("reader"), Set.of("runner"), Set.of("writer"), Set.of("owner"));
    assertEquals(aclVersion, service.getAuthorizationVersion());

    service.setRoles("owner", Set.of("group"));
    long roleVersion = service.getAuthorizationVersion();
    assertEquals(aclVersion + 1, roleVersion);
    service.setRoles("owner", Set.of("group"));
    assertEquals(roleVersion, service.getAuthorizationVersion());
  }

  @Test
  void staleAuthorizationVersionRejectsGuardedFolderMutation() throws Exception {
    AuthorizationService service = newAuthorizationService();
    service.createNoteAuth("note-id", new AuthenticationInfo("owner"));
    long beforeRoleChange = service.getAuthorizationVersion();
    service.setRoles("owner", Set.of("new-role"));
    AtomicBoolean executed = new AtomicBoolean();

    assertThrows(
        IOException.class,
        () ->
            service.runWithAuthorizationVersion(
                beforeRoleChange,
                () -> {
                  executed.set(true);
                  return null;
                }));

    assertFalse(executed.get());
  }

  @Test
  void guardedFolderMutationCannotInterleaveWithAclChange() throws Exception {
    AuthorizationService service = newAuthorizationService();
    service.createNoteAuth("note-id", new AuthenticationInfo("owner"));
    long authorizedVersion = service.getAuthorizationVersion();
    CountDownLatch operationStarted = new CountDownLatch(1);
    CountDownLatch releaseOperation = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<Void> guardedOperation = executor.submit(
          () -> service.runWithAuthorizationVersion(
              authorizedVersion,
              () -> {
                operationStarted.countDown();
                try {
                  releaseOperation.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new IOException("Interrupted while testing authorization lock", e);
                }
                return null;
              }));
      assertTrue(operationStarted.await(5, TimeUnit.SECONDS));

      Future<?> aclChange = executor.submit(
          () -> {
            service.setOwners("note-id", Set.of("new-owner"));
            return null;
          });
      assertThrows(TimeoutException.class, () -> aclChange.get(200, TimeUnit.MILLISECONDS));

      releaseOperation.countDown();
      guardedOperation.get(5, TimeUnit.SECONDS);
      aclChange.get(5, TimeUnit.SECONDS);
      assertEquals(Set.of("new-owner"), service.getOwners("note-id"));
    } finally {
      releaseOperation.countDown();
      executor.shutdownNow();
    }
  }

  private static AuthorizationService newAuthorizationService() {
    NoteManager noteManager = mock(NoteManager.class);
    when(noteManager.getNotesInfo()).thenReturn(Collections.emptyMap());
    return new AuthorizationService(
        noteManager, mock(ZeppelinConfiguration.class), mock(ConfigStorage.class));
  }
}
