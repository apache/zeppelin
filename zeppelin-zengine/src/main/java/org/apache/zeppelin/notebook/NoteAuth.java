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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.user.AuthenticationInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represent note authorization info, including (readers, writers, runners, owners)
 *
 */
public class NoteAuth {

  private static Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private String noteId;
  private Set<String> readers = new HashSet<>();
  private Set<String> writers = new HashSet<>();
  private Set<String> runners = new HashSet<>();
  private Set<String> owners = new HashSet<>();

  public NoteAuth(String noteId) {
    this(noteId, AuthenticationInfo.ANONYMOUS);
  }

  public NoteAuth(String noteId, AuthenticationInfo subject) {
    this.noteId = noteId;
    initPermissions(subject);
  }

  public NoteAuth(String noteId, Map<String, Set<String>> permissions) {
    this.noteId = noteId;
    this.readers = permissions.getOrDefault("readers", new HashSet<>());
    this.writers = permissions.getOrDefault("writers", new HashSet<>());
    this.runners = permissions.getOrDefault("runners", new HashSet<>());
    this.owners = permissions.getOrDefault("owners", new HashSet<>());
  }

  // used when creating new note
  public void initPermissions(AuthenticationInfo subject) {
    if (!AuthenticationInfo.isAnonymous(subject)) {
      if (ZeppelinConfiguration.create().isNotebookPublic()) {
        // add current user to owners - can be public
        this.owners.add(checkCaseAndConvert(subject.getUser()));
      } else {
        // add current user to owners, readers, runners, writers - private note
        this.owners.add(checkCaseAndConvert(subject.getUser()));
        this.readers.add(checkCaseAndConvert(subject.getUser()));
        this.writers.add(checkCaseAndConvert(subject.getUser()));
        this.runners.add(checkCaseAndConvert(subject.getUser()));
      }
    }
  }

  public String getNoteId() {
    return noteId;
  }

  public void setOwners(Set<String> entities) {
    this.owners = checkCaseAndConvert(entities);
  }

  public void setReaders(Set<String> entities) {
    this.readers = checkCaseAndConvert(entities);
  }

  public void setWriters(Set<String> entities) {
    this.writers = checkCaseAndConvert(entities);
  }

  public void setRunners(Set<String> entities) {
    this.runners = checkCaseAndConvert(entities);
  }

  public Set<String> getOwners() {
    return this.owners;
  }

  public Set<String> getReaders() {
    return this.readers;
  }

  public Set<String> getWriters() {
    return this.writers;
  }

  public Set<String> getRunners() {
    return this.runners;
  }

  /*
   * If case conversion is enforced, then change entity names to lower case
   */
  private Set<String> checkCaseAndConvert(Set<String> entities) {
    if (ZeppelinConfiguration.create().isUsernameForceLowerCase()) {
      Set<String> set2 = new HashSet<>();
      for (String name : entities) {
        set2.add(name.toLowerCase());
      }
      return set2;
    } else {
      return entities;
    }
  }

  private String checkCaseAndConvert(String entity) {
    if (ZeppelinConfiguration.create().isUsernameForceLowerCase()) {
      return entity.toLowerCase();
    } else {
      return entity;
    }
  }

  public Map<String, Set<String>> toMap() {
    Map<String, Set<String>> map = new HashMap<>();
    map.put("readers", readers);
    map.put("writers", writers);
    map.put("runners", runners);
    map.put("owners", owners);
    return map;
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static NoteAuth fromJson(String json) {
    return gson.fromJson(json, NoteAuth.class);
  }

}
