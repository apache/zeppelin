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

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is responsible for maintain notes authorization info. And provide api for
 * setting and querying note authorization info.
 */
public class AuthorizationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationService.class);
  private static final Set<String> EMPTY_SET = Collections.emptySet();

  private final ZeppelinConfiguration zConf;
  private final ConfigStorage configStorage;

  // contains roles for each user (username --> roles)
  private Map<String, Set<String>> userRoles = new ConcurrentHashMap<>();

  // cached note permission info. (noteId --> NoteAuth)
  private Map<String, NoteAuth> notesAuth = new ConcurrentHashMap<>();

  /**
   * Monotonic generation for every effective ACL or cached-role change.
   *
   * <p>Folder operations authorize a metadata snapshot outside this monitor, then reacquire the
   * monitor through {@link #runWithAuthorizationVersion(long, AuthorizationOperation)} before
   * mutating the repository. This prevents an ACL or role change from being interleaved between
   * descendant authorization and a destructive folder mutation.
   */
  private long authorizationVersion;

  @Inject
  public AuthorizationService(NoteManager noteManager, ZeppelinConfiguration zConf,
      ConfigStorage storage) {
    LOGGER.info("Injected AuthorizationService: {}", this);
    this.zConf = zConf;
    this.configStorage = storage;
    try {
      // init notesAuth by reading notebook-authorization.json
      NotebookAuthorizationInfoSaving authorizationInfoSaving = configStorage.loadNotebookAuthorization();
      if (authorizationInfoSaving != null) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : authorizationInfoSaving.getAuthInfo().entrySet()) {
          String noteId = entry.getKey();
          Map<String, Set<String>> permissions = entry.getValue();
          notesAuth.put(noteId, new NoteAuth(noteId, permissions, zConf));
        }
      }

      // initialize NoteAuth for the notes without permission set explicitly.
      for (String noteId : noteManager.getNotesInfo().keySet()) {
        if (!notesAuth.containsKey(noteId)) {
          notesAuth.put(noteId, new NoteAuth(noteId, zConf));
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Fail to create ConfigStorage", e);
    }
  }

  /**
   * Create NoteAuth, this method only create NoteAuth in memory, you need to call method
   * saveNoteAuth to persistent it to storage.
   * @param noteId
   * @param subject
   * @throws IOException
   */
  public synchronized void createNoteAuth(String noteId, AuthenticationInfo subject) {
    NoteAuth noteAuth = new NoteAuth(noteId, subject, zConf);
    this.notesAuth.put(noteId, noteAuth);
    authorizationVersion++;
  }

  /**
   * Persistent NoteAuth
   *
   * @throws IOException
   */
  public synchronized void saveNoteAuth() throws IOException {
    configStorage.save(new NotebookAuthorizationInfoSaving(this.notesAuth));
  }

  public synchronized void removeNoteAuth(String noteId) {
    if (this.notesAuth.remove(noteId) != null) {
      authorizationVersion++;
    }
  }

  public synchronized long getAuthorizationVersion() {
    return authorizationVersion;
  }

  public synchronized boolean isAuthorizationVersionCurrent(long expectedVersion) {
    return authorizationVersion == expectedVersion;
  }

  /**
   * Run one operation only if its authorization preflight still belongs to the current ACL
   * generation. ACL and cached-role mutations use the same monitor and therefore cannot be
   * interleaved with the guarded operation.
   */
  public synchronized <T> T runWithAuthorizationVersion(
      long expectedVersion, AuthorizationOperation<T> operation) throws IOException {
    if (authorizationVersion != expectedVersion) {
      throw new IOException("Notebook authorization changed while authorizing the operation");
    }
    return operation.run();
  }

  @FunctionalInterface
  public interface AuthorizationOperation<T> {
    T run() throws IOException;
  }

  public boolean hasNoteAuth(String noteId) {
    return this.notesAuth.containsKey(noteId);
  }

  // skip empty user and remove the white space around user name.
  private Set<String> normalizeUsers(Set<String> users) {
    Set<String> returnUser = new HashSet<>();
    for (String user : users) {
      if (!user.trim().isEmpty()) {
        returnUser.add(user.trim());
      }
    }
    return returnUser;
  }

  public void setOwners(String noteId, Set<String> entities) throws IOException {
    setOwners(noteId, entities, true);
  }

  public void setReaders(String noteId, Set<String> entities) throws IOException {
    setReaders(noteId, entities, true);
  }

  public void setWriters(String noteId, Set<String> entities) throws IOException {
    setWriters(noteId, entities, true);
  }

  public void setRunners(String noteId, Set<String> entities) throws IOException {
    setRunners(noteId, entities, true);
  }

  public void setPermissions(
      String noteId,
      Set<String> readers,
      Set<String> runners,
      Set<String> writers,
      Set<String> owners) throws IOException {
    setPermissions(noteId, readers, runners, writers, owners, true);
  }

  public void setRoles(String user, Set<String> roles) {
    setRoles(user, roles, true);
  }

  public void clearPermission(String noteId) throws IOException {
    clearPermission(noteId, true);
  }

  public synchronized void setOwners(
      String noteId, Set<String> entities, boolean broadcast) throws IOException {
    entities = normalizeUsers(entities);
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      throw new IOException("No noteAuth found for noteId: " + noteId);
    }
    if (!noteAuth.getOwners().equals(entities)) {
      noteAuth.setOwners(entities);
      authorizationVersion++;
    }
  }

  public synchronized void setReaders(
      String noteId, Set<String> entities, boolean broadcast) throws IOException {
    entities = normalizeUsers(entities);
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      throw new IOException("No noteAuth found for noteId: " + noteId);
    }
    if (!noteAuth.getReaders().equals(entities)) {
      noteAuth.setReaders(entities);
      authorizationVersion++;
    }
  }

  public synchronized void setRunners(
      String noteId, Set<String> entities, boolean broadcast) throws IOException {
    entities = normalizeUsers(entities);
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      throw new IOException("No noteAuth found for noteId: " + noteId);
    }
    if (!noteAuth.getRunners().equals(entities)) {
      noteAuth.setRunners(entities);
      authorizationVersion++;
    }
  }

  public synchronized void setWriters(
      String noteId, Set<String> entities, boolean broadcast) throws IOException {
    entities = normalizeUsers(entities);
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      throw new IOException("No noteAuth found for noteId: " + noteId);
    }
    if (!noteAuth.getWriters().equals(entities)) {
      noteAuth.setWriters(entities);
      authorizationVersion++;
    }
  }

  public synchronized void setPermissions(
      String noteId,
      Set<String> readers,
      Set<String> runners,
      Set<String> writers,
      Set<String> owners,
      boolean broadcast) throws IOException {
    Set<String> normalizedReaders = normalizeUsers(readers);
    Set<String> normalizedRunners = normalizeUsers(runners);
    Set<String> normalizedWriters = normalizeUsers(writers);
    Set<String> normalizedOwners = normalizeUsers(owners);
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      throw new IOException("No noteAuth found for noteId: " + noteId);
    }
    NoteAuth.Permissions current = noteAuth.getPermissions();
    if (!current.getReaders().equals(normalizedReaders)
        || !current.getRunners().equals(normalizedRunners)
        || !current.getWriters().equals(normalizedWriters)
        || !current.getOwners().equals(normalizedOwners)) {
      noteAuth.setPermissions(
          normalizedReaders, normalizedRunners, normalizedWriters, normalizedOwners);
      authorizationVersion++;
    }
  }

  public synchronized void setRoles(String user, Set<String> roles, boolean broadcast) {
    if (StringUtils.isBlank(user)) {
      LOGGER.warn("Setting roles for empty user");
      return;
    }
    roles = normalizeUsers(roles);
    Set<String> immutableRoles = Collections.unmodifiableSet(new HashSet<>(roles));
    Set<String> previousRoles = userRoles.put(user, immutableRoles);
    if (!immutableRoles.equals(previousRoles)) {
      authorizationVersion++;
    }
  }

  public void clearPermission(String noteId, boolean broadcast) throws IOException {
    setPermissions(
        noteId,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        broadcast);
  }

  public Set<String> getOwners(String noteId) {
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      LOGGER.warn("No noteAuth found for noteId: {}", noteId);
      return EMPTY_SET;
    }
    return noteAuth.getOwners();
  }

  public Set<String> getReaders(String noteId) {
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      LOGGER.warn("No noteAuth found for noteId: {}", noteId);
      return EMPTY_SET;
    }
    return noteAuth.getReaders();
  }

  public Set<String> getRunners(String noteId) {
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      LOGGER.warn("No noteAuth found for noteId: {}", noteId);
      return EMPTY_SET;
    }
    return noteAuth.getRunners();
  }

  public Set<String> getWriters(String noteId) {
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      LOGGER.warn("No noteAuth found for noteId: {}", noteId);
      return EMPTY_SET;
    }
    return noteAuth.getWriters();
  }

  public Set<String> getRoles(String user) {
    return new HashSet<>(userRoles.getOrDefault(user, EMPTY_SET));
  }

  public boolean isOwner(String noteId, Set<String> entities) {
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      return false;
    }
    NoteAuth.Permissions permissions = noteAuth.getPermissions();
    return isMember(entities, permissions.getOwners()) || isAdmin(entities);
  }

  public boolean isWriter(String noteId, Set<String> entities) {
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      return false;
    }
    NoteAuth.Permissions permissions = noteAuth.getPermissions();
    return isMember(entities, permissions.getWriters())
        || isMember(entities, permissions.getOwners())
        || isAdmin(entities);
  }

  public boolean isReader(String noteId, Set<String> entities) {
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      return false;
    }
    NoteAuth.Permissions permissions = noteAuth.getPermissions();
    return isMember(entities, permissions.getReaders())
        || isMember(entities, permissions.getOwners())
        || isMember(entities, permissions.getWriters())
        || isMember(entities, permissions.getRunners())
        || isAdmin(entities);
  }

  public boolean isRunner(String noteId, Set<String> entities) {
    NoteAuth noteAuth = notesAuth.get(noteId);
    if (noteAuth == null) {
      return false;
    }
    NoteAuth.Permissions permissions = noteAuth.getPermissions();
    return isMember(entities, permissions.getRunners())
        || isMember(entities, permissions.getWriters())
        || isMember(entities, permissions.getOwners())
        || isAdmin(entities);
  }

  private boolean isAdmin(Set<String> entities) {
    String adminRole = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_OWNER_ROLE);
    if (StringUtils.isBlank(adminRole)) {
      return false;
    }
    return entities.contains(adminRole);
  }

  // return true if b is empty or if (a intersection b) is non-empty
  private boolean isMember(Set<String> a, Set<String> b) {
    Set<String> intersection = new HashSet<>(b);
    intersection.retainAll(a);
    return (b.isEmpty() || (intersection.size() > 0));
  }

  public boolean isOwner(Set<String> userAndRoles, String noteId) {
    if (!hasNoteAuth(noteId)) {
      return false;
    }
    if (zConf.isAnonymousAllowed()) {
      LOGGER.debug("Zeppelin runs in anonymous mode, everybody is owner");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isOwner(noteId, userAndRoles);
  }

  //TODO(zjffdu) merge this hasWritePermission with isWriter ?
  public boolean hasWritePermission(Set<String> userAndRoles, String noteId) {
    if (!hasNoteAuth(noteId)) {
      return false;
    }
    if (zConf.isAnonymousAllowed()) {
      LOGGER.debug("Zeppelin runs in anonymous mode, everybody is writer");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isWriter(noteId, userAndRoles);
  }

  public boolean hasReadPermission(Set<String> userAndRoles, String noteId) {
    if (!hasNoteAuth(noteId)) {
      return false;
    }
    if (zConf.isAnonymousAllowed()) {
      LOGGER.debug("Zeppelin runs in anonymous mode, everybody is reader");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isReader(noteId, userAndRoles);
  }

  public boolean hasRunPermission(Set<String> userAndRoles, String noteId) {
    if (!hasNoteAuth(noteId)) {
      return false;
    }
    if (zConf.isAnonymousAllowed()) {
      LOGGER.debug("Zeppelin runs in anonymous mode, everybody is reader");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isRunner(noteId, userAndRoles);
  }

  public boolean isPublic() {
    return zConf.isNotebookPublic();
  }

}
