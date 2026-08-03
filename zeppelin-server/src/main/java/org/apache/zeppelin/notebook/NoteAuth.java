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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.user.AuthenticationInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represent note authorization info, including (readers, writers, runners, owners)
 *
 */
public class NoteAuth {

  private final String noteId;
  private final ZeppelinConfiguration zConf;

  private volatile Permissions permissions = Permissions.empty();

  public NoteAuth(String noteId, ZeppelinConfiguration zConf) {
    this(noteId, AuthenticationInfo.ANONYMOUS, zConf);
  }

  public NoteAuth(String noteId, AuthenticationInfo subject, ZeppelinConfiguration zConf) {
    this.noteId = noteId;
    this.zConf = zConf;
    initPermissions(subject);
  }

  /**
   * Creates a NoteAuth from a map loaded from notebook-authorization.json. At this point it is not possible to distinguish
   * between a user and a group string, so checkCaseAndConvert must not be used.
   *
   * @param noteId
   * @param permissions
   * @param zConf
   */
  public NoteAuth(String noteId, Map<String, Set<String>> permissions, ZeppelinConfiguration zConf) {
    this.noteId = noteId;
    this.zConf = zConf;
    this.permissions =
        new Permissions(
            immutableLoadedEntities(
                permissions.getOrDefault("readers", Collections.emptySet())),
            immutableLoadedEntities(
                permissions.getOrDefault("runners", Collections.emptySet())),
            immutableLoadedEntities(
                permissions.getOrDefault("writers", Collections.emptySet())),
            immutableLoadedEntities(
                permissions.getOrDefault("owners", Collections.emptySet())));
  }

  // used when creating new note
  public synchronized void initPermissions(AuthenticationInfo subject) {
    Set<String> readers = Collections.emptySet();
    Set<String> writers = Collections.emptySet();
    Set<String> runners = Collections.emptySet();
    Set<String> owners = Collections.emptySet();
    if (!AuthenticationInfo.isAnonymous(subject)) {
      Set<String> owner = Collections.singleton(checkCaseAndConvert(subject.getUser()));
      if (zConf.isNotebookPublic()) {
        // add current user to owners - can be public
        owners = owner;
      } else {
        // add current user to owners, readers, runners, writers - private note
        owners = owner;
        readers = owner;
        writers = owner;
        runners = owner;
      }
    }
    setPermissions(readers, runners, writers, owners);
  }

  public String getNoteId() {
    return noteId;
  }

  public synchronized void setOwners(Set<String> entities) {
    Permissions current = permissions;
    permissions =
        new Permissions(
            current.getReaders(),
            current.getRunners(),
            current.getWriters(),
            immutableEntities(entities));
  }

  public synchronized void setReaders(Set<String> entities) {
    Permissions current = permissions;
    permissions =
        new Permissions(
            immutableEntities(entities),
            current.getRunners(),
            current.getWriters(),
            current.getOwners());
  }

  public synchronized void setWriters(Set<String> entities) {
    Permissions current = permissions;
    permissions =
        new Permissions(
            current.getReaders(),
            current.getRunners(),
            immutableEntities(entities),
            current.getOwners());
  }

  public synchronized void setRunners(Set<String> entities) {
    Permissions current = permissions;
    permissions =
        new Permissions(
            current.getReaders(),
            immutableEntities(entities),
            current.getWriters(),
            current.getOwners());
  }

  public synchronized void setPermissions(
      Set<String> readers, Set<String> runners, Set<String> writers, Set<String> owners) {
    permissions =
        new Permissions(
            immutableEntities(readers),
            immutableEntities(runners),
            immutableEntities(writers),
            immutableEntities(owners));
  }

  public Set<String> getOwners() {
    return permissions.getOwners();
  }

  public Set<String> getReaders() {
    return permissions.getReaders();
  }

  public Set<String> getWriters() {
    return permissions.getWriters();
  }

  public Set<String> getRunners() {
    return permissions.getRunners();
  }

  Permissions getPermissions() {
    return permissions;
  }

  /*
   * If case conversion is enforced, then change entity names to lower case
   */
  private Set<String> checkCaseAndConvert(Set<String> entities) {
    if (zConf.isUsernameForceLowerCase()) {
      Set<String> set2 = new HashSet<>();
      for (String name : entities) {
        set2.add(name.toLowerCase());
      }
      return set2;
    } else {
      return new HashSet<>(entities);
    }
  }

  private Set<String> immutableEntities(Set<String> entities) {
    return Collections.unmodifiableSet(checkCaseAndConvert(entities));
  }

  private Set<String> immutableLoadedEntities(Set<String> entities) {
    return Collections.unmodifiableSet(new HashSet<>(entities));
  }

  private String checkCaseAndConvert(String entity) {
    if (zConf.isUsernameForceLowerCase()) {
      return entity.toLowerCase();
    } else {
      return entity;
    }
  }

  public Map<String, Set<String>> toMap() {
    return permissions.toMap();
  }

  static final class Permissions {
    private final Set<String> readers;
    private final Set<String> runners;
    private final Set<String> writers;
    private final Set<String> owners;

    private Permissions(
        Set<String> readers, Set<String> runners, Set<String> writers, Set<String> owners) {
      this.readers = readers;
      this.runners = runners;
      this.writers = writers;
      this.owners = owners;
    }

    private static Permissions empty() {
      return new Permissions(
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet());
    }

    Set<String> getReaders() {
      return readers;
    }

    Set<String> getRunners() {
      return runners;
    }

    Set<String> getWriters() {
      return writers;
    }

    Set<String> getOwners() {
      return owners;
    }

    Map<String, Set<String>> toMap() {
      Map<String, Set<String>> map = new HashMap<>();
      map.put("readers", readers);
      map.put("writers", writers);
      map.put("runners", runners);
      map.put("owners", owners);
      return map;
    }
  }
}
