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

import java.io.IOException;
import java.lang.reflect.GenericSignatureFormatError;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.cluster.ClusterManagerServer;
import org.apache.zeppelin.cluster.event.ClusterEvent;
import org.apache.zeppelin.cluster.event.ClusterEventListener;
import org.apache.zeppelin.cluster.event.ClusterMessage;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;

/**
 * Contains authorization information for notes
 */
public class NotebookAuthorization implements NoteEventListener, ClusterEventListener {
  private static final Logger LOG = LoggerFactory.getLogger(NotebookAuthorization.class);
  private static NotebookAuthorization instance = null;
  /*
   * { "note1": { "owners": ["u1"], "readers": ["u1", "u2"], "runners": ["u2"],
   * "writers": ["u1"] },  "note2": ... } }
   */
  private static Map<String, Map<String, Set<String>>> authInfo = new HashMap<>();
  /*
   * contains roles for each user
   */
  private static Map<String, Set<String>> userRoles = new HashMap<>();
  private static ZeppelinConfiguration conf;

  private static ConfigStorage configStorage;

  private NotebookAuthorization() {}

  public static NotebookAuthorization init(ZeppelinConfiguration config) {
    if (instance == null) {
      instance = new NotebookAuthorization();
      conf = config;
      ClusterManagerServer.getInstance().addClusterEventListeners(
          ClusterManagerServer.CLUSTER_NB_AUTH_EVENT_TOPIC, instance);
      try {
        configStorage = ConfigStorage.getInstance(config);
        loadFromFile();
      } catch (IOException e) {
        LOG.error("Error loading NotebookAuthorization", e);
      }
    }
    return instance;
  }

  public static NotebookAuthorization getInstance() {
    if (instance == null) {
      LOG.warn("Notebook authorization module was called without initialization,"
          + " initializing with default configuration");
      init(ZeppelinConfiguration.create());
    }
    return instance;
  }

  private static void loadFromFile() throws IOException {
    NotebookAuthorizationInfoSaving info = configStorage.loadNotebookAuthorization();
    if (info != null) {
      authInfo = info.authInfo;
    }
  }
  
  public void setRoles(String user, Set<String> roles) {
    if (StringUtils.isBlank(user)) {
      LOG.warn("Setting roles for empty user");
      return;
    }
    roles = validateUser(roles);
    userRoles.put(user, roles);
  }
  
  public Set<String> getRoles(String user) {
    Set<String> roles = Sets.newHashSet();
    if (userRoles.containsKey(user)) {
      roles.addAll(userRoles.get(user));
    }
    return roles;
  }
  
  private void saveToFile() {
    synchronized (authInfo) {
      NotebookAuthorizationInfoSaving info = new NotebookAuthorizationInfoSaving();
      info.authInfo = authInfo;
      try {
        configStorage.save(info);
      } catch (IOException e) {
        LOG.error("Error saving notebook authorization file", e);
      }
    }
  }
  
  public boolean isPublic() {
    return conf.isNotebookPublic();
  }

  private Set<String> validateUser(Set<String> users) {
    Set<String> returnUser = new HashSet<>();
    for (String user : users) {
      if (!user.trim().isEmpty()) {
        returnUser.add(user.trim());
      }
    }
    return returnUser;
  }

  public void setOwners(String noteId, Set<String> entities) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    entities = validateUser(entities);
    if (noteAuthInfo == null) {
      noteAuthInfo = new LinkedHashMap();
      noteAuthInfo.put("owners", new LinkedHashSet(entities));
      noteAuthInfo.put("readers", new LinkedHashSet());
      noteAuthInfo.put("runners", new LinkedHashSet());
      noteAuthInfo.put("writers", new LinkedHashSet());
    } else {
      noteAuthInfo.put("owners", new LinkedHashSet(entities));
    }
    authInfo.put(noteId, noteAuthInfo);
    saveToFile();
  }

  public void setReaders(String noteId, Set<String> entities) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    entities = validateUser(entities);
    if (noteAuthInfo == null) {
      noteAuthInfo = new LinkedHashMap();
      noteAuthInfo.put("owners", new LinkedHashSet());
      noteAuthInfo.put("readers", new LinkedHashSet(entities));
      noteAuthInfo.put("runners", new LinkedHashSet());
      noteAuthInfo.put("writers", new LinkedHashSet());
    } else {
      noteAuthInfo.put("readers", new LinkedHashSet(entities));
    }
    authInfo.put(noteId, noteAuthInfo);
    saveToFile();
  }

  public void setRunners(String noteId, Set<String> entities) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    entities = validateUser(entities);
    if (noteAuthInfo == null) {
      noteAuthInfo = new LinkedHashMap();
      noteAuthInfo.put("owners", new LinkedHashSet());
      noteAuthInfo.put("readers", new LinkedHashSet());
      noteAuthInfo.put("runners", new LinkedHashSet(entities));
      noteAuthInfo.put("writers", new LinkedHashSet());
    } else {
      noteAuthInfo.put("runners", new LinkedHashSet(entities));
    }
    authInfo.put(noteId, noteAuthInfo);
    saveToFile();
  }


  public void setWriters(String noteId, Set<String> entities) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    entities = validateUser(entities);
    if (noteAuthInfo == null) {
      noteAuthInfo = new LinkedHashMap();
      noteAuthInfo.put("owners", new LinkedHashSet());
      noteAuthInfo.put("readers", new LinkedHashSet());
      noteAuthInfo.put("runners", new LinkedHashSet());
      noteAuthInfo.put("writers", new LinkedHashSet(entities));
    } else {
      noteAuthInfo.put("writers", new LinkedHashSet(entities));
    }
    authInfo.put(noteId, noteAuthInfo);
    saveToFile();
  }

  /*
  * If case conversion is enforced, then change entity names to lower case
  */
  private Set<String> checkCaseAndConvert(Set<String> entities) {
    if (conf.isUsernameForceLowerCase()) {
      Set<String> set2 = new HashSet<String>();
      for (String name : entities) {
        set2.add(name.toLowerCase());
      }
      return set2;
    } else {
      return entities;
    }
  }

  public Set<String> getOwners(String noteId) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    Set<String> entities = null;
    if (noteAuthInfo == null) {
      entities = new HashSet<>();
    } else {
      entities = noteAuthInfo.get("owners");
      if (entities == null) {
        entities = new HashSet<>();
      } else {
        entities = checkCaseAndConvert(entities);
      }
    }
    return entities;
  }

  public Set<String> getReaders(String noteId) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    Set<String> entities = null;
    if (noteAuthInfo == null) {
      entities = new HashSet<>();
    } else {
      entities = noteAuthInfo.get("readers");
      if (entities == null) {
        entities = new HashSet<>();
      } else {
        entities = checkCaseAndConvert(entities);
      }
    }
    return entities;
  }

  public Set<String> getRunners(String noteId) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    Set<String> entities = null;
    if (noteAuthInfo == null) {
      entities = new HashSet<>();
    } else {
      entities = noteAuthInfo.get("runners");
      if (entities == null) {
        entities = new HashSet<>();
      } else {
        entities = checkCaseAndConvert(entities);
      }
    }
    return entities;
  }

  public Set<String> getWriters(String noteId) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    Set<String> entities = null;
    if (noteAuthInfo == null) {
      entities = new HashSet<>();
    } else {
      entities = noteAuthInfo.get("writers");
      if (entities == null) {
        entities = new HashSet<>();
      } else {
        entities = checkCaseAndConvert(entities);
      }
    }
    return entities;
  }

  public boolean isOwner(String noteId, Set<String> entities) {
    return isMember(entities, getOwners(noteId)) || isAdmin(entities);
  }

  public boolean isWriter(String noteId, Set<String> entities) {
    return isMember(entities, getWriters(noteId)) ||
           isMember(entities, getOwners(noteId)) ||
           isAdmin(entities);
  }

  public boolean isReader(String noteId, Set<String> entities) {
    return isMember(entities, getReaders(noteId)) ||
           isMember(entities, getOwners(noteId)) ||
           isMember(entities, getWriters(noteId)) ||
           isMember(entities, getRunners(noteId)) ||
           isAdmin(entities);
  }

  public boolean isRunner(String noteId, Set<String> entities) {
    return isMember(entities, getRunners(noteId)) ||
           isMember(entities, getWriters(noteId)) ||
           isMember(entities, getOwners(noteId)) ||
           isAdmin(entities);
  }

  private boolean isAdmin(Set<String> entities) {
    String adminRole = conf.getString(ConfVars.ZEPPELIN_OWNER_ROLE);
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
    if (conf.isAnonymousAllowed()) {
      LOG.debug("Zeppelin runs in anonymous mode, everybody is owner");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isOwner(noteId, userAndRoles);
  }
  
  public boolean hasWriteAuthorization(Set<String> userAndRoles, String noteId) {
    if (conf.isAnonymousAllowed()) {
      LOG.debug("Zeppelin runs in anonymous mode, everybody is writer");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isWriter(noteId, userAndRoles);
  }
  
  public boolean hasReadAuthorization(Set<String> userAndRoles, String noteId) {
    if (conf.isAnonymousAllowed()) {
      LOG.debug("Zeppelin runs in anonymous mode, everybody is reader");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isReader(noteId, userAndRoles);
  }

  public boolean hasRunAuthorization(Set<String> userAndRoles, String noteId) {
    if (conf.isAnonymousAllowed()) {
      LOG.debug("Zeppelin runs in anonymous mode, everybody is runner");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isRunner(noteId, userAndRoles);
  }

  public void removeNote(String noteId) {
    authInfo.remove(noteId);
    saveToFile();
  }

  public List<NoteInfo> filterByUser(List<NoteInfo> notes, AuthenticationInfo subject) {
    final Set<String> entities = Sets.newHashSet();
    if (subject != null) {
      entities.add(subject.getUser());
    }
    return FluentIterable.from(notes).filter(new Predicate<NoteInfo>() {
      @Override
      public boolean apply(NoteInfo input) {
        return input != null && isReader(input.getId(), entities);
      }
    }).toList();
  }

  public void setNewNotePermissions(String noteId, AuthenticationInfo subject) {
    inlineSetNewNotePermissions(noteId, subject);
    broadcastClusterEvent(ClusterEvent.SET_NEW_NOTE_PERMISSIONS, noteId, subject);
  }
  
  public void inlineSetNewNotePermissions(String noteId, AuthenticationInfo subject) {
    if (!AuthenticationInfo.isAnonymous(subject)) {
      if (isPublic()) {
        // add current user to owners - can be public
        Set<String> owners = getOwners(noteId);
        owners.add(subject.getUser());
        setOwners(noteId, owners);
      } else {
        Map<ClusterEvent, Set<String>> mapEntities = new HashMap<>();
        // add current user to owners, readers, runners, writers - private note
        Set<String> entities = getOwners(noteId);
        entities.add(subject.getUser());
        setOwners(noteId, entities);

        entities = getReaders(noteId);
        entities.add(subject.getUser());
        setReaders(noteId, entities);

        entities = getRunners(noteId);
        entities.add(subject.getUser());
        setRunners(noteId, entities);

        entities = getWriters(noteId);
        entities.add(subject.getUser());
        setWriters(noteId, entities);
      }
    }
  }

  @Override
  public void onNoteCreate(Note note, AuthenticationInfo subject) {
    setNewNotePermissions(note.getId(), subject);
  }

  @Override
  public void onNoteRemove(Note note, AuthenticationInfo subject) {
    removeNote(note.getId());
  }

  @Override
  public void onNoteUpdate(Note note, AuthenticationInfo subject) {

  }

  @Override
  public void onParagraphRemove(Paragraph p) {

  }

  @Override
  public void onParagraphCreate(Paragraph p) {

  }

  @Override
  public void onParagraphUpdate(Paragraph p) throws IOException {

  }

  @Override
  public void onParagraphStatusChange(Paragraph p, Job.Status status) {

  }

  @Override
  public void onClusterEvent(String msg) {
    if (LOG.isDebugEnabled()) {
      LOG.info("onClusterEvent : {}", msg);
    }
    ClusterMessage message = ClusterMessage.deserializeMessage(msg);
    String noteId  = message.get("noteId");
    String json  = message.get("subject");
    AuthenticationInfo subject = AuthenticationInfo.fromJson(json);

    inlineSetNewNotePermissions(noteId, subject);
  }

  // broadcast cluster event
  private void broadcastClusterEvent(ClusterEvent event, String noteId, AuthenticationInfo subject) {
    if (!conf.isClusterMode()) {
      return;
    }

    ClusterMessage message = new ClusterMessage(event);
    message.put("noteId", noteId);
    message.put("subject", subject.toJson());
    String msg = ClusterMessage.serializeMessage(message);
    ClusterManagerServer.getInstance().broadcastClusterEvent(
        ClusterManagerServer.CLUSTER_AUTH_EVENT_TOPIC, msg);
  }
}
