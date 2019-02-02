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

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is responsible for maintain notes authorization info. And provide api for
 * setting and querying note authorization info.
 */
public class AuthorizationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationService.class);
  private static final Set<String> EMPTY_SET = new HashSet<>();

  private ZeppelinConfiguration conf;
  private Notebook notebook;
  /*
   * contains roles for each user
   */
  private Map<String, Set<String>> userRoles = new HashMap<>();

  @Inject
  public AuthorizationService(Notebook notebook, ZeppelinConfiguration conf) {
    this.notebook = notebook;
    this.conf = conf;
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
    entities = validateUser(entities);
    notebook.getNote(noteId).setOwners(entities);
  }

  public void setReaders(String noteId, Set<String> entities) {
    entities = validateUser(entities);
    notebook.getNote(noteId).setReaders(entities);
  }

  public void setRunners(String noteId, Set<String> entities) {
    entities = validateUser(entities);
    notebook.getNote(noteId).setRunners(entities);
  }

  public void setWriters(String noteId, Set<String> entities) {
    entities = validateUser(entities);
    notebook.getNote(noteId).setWriters(entities);
  }

  public Set<String> getOwners(String noteId) {
    Set<String> entities = notebook.getNote(noteId).getOwners();
    if (entities != null) {
      return entities;
    } else {
      return EMPTY_SET ;
    }
  }

  public Set<String> getReaders(String noteId) {
    Set<String> entities = notebook.getNote(noteId).getReaders();
    if (entities != null) {
      return entities;
    } else {
      return EMPTY_SET ;
    }
  }

  public Set<String> getRunners(String noteId) {
    Set<String> entities = notebook.getNote(noteId).getRunners();
    if (entities != null) {
      return entities;
    } else {
      return EMPTY_SET ;
    }
  }

  public Set<String> getWriters(String noteId) {
    Set<String> entities = notebook.getNote(noteId).getWriters();
    if (entities != null) {
      return entities;
    } else {
      return EMPTY_SET ;
    }
  }

  public boolean isOwner(String noteId, Set<String> entities) {
    return isMember(entities, notebook.getNote(noteId).getOwners()) || isAdmin(entities);
  }

  public boolean isWriter(String noteId, Set<String> entities) {
    return isMember(entities, notebook.getNote(noteId).getWriters()) ||
            isMember(entities, notebook.getNote(noteId).getOwners()) ||
            isAdmin(entities);
  }

  public boolean isReader(String noteId, Set<String> entities) {
    return isMember(entities, notebook.getNote(noteId).getReaders()) ||
            isMember(entities, notebook.getNote(noteId).getOwners()) ||
            isMember(entities, notebook.getNote(noteId).getWriters()) ||
            isMember(entities, notebook.getNote(noteId).getRunners()) ||
            isAdmin(entities);
  }

  public boolean isRunner(String noteId, Set<String> entities) {
    return isMember(entities, notebook.getNote(noteId).getRunners()) ||
            isMember(entities, notebook.getNote(noteId).getWriters()) ||
            isMember(entities, notebook.getNote(noteId).getOwners()) ||
            isAdmin(entities);
  }

  private boolean isAdmin(Set<String> entities) {
    String adminRole = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_OWNER_ROLE);
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
    if (conf.isAnonymousAllowed()) {
      LOGGER.debug("Zeppelin runs in anonymous mode, everybody is writer");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isWriter(noteId, userAndRoles);
  }

  public boolean hasReadPermission(Set<String> userAndRoles, String noteId) {
    if (conf.isAnonymousAllowed()) {
      LOGGER.debug("Zeppelin runs in anonymous mode, everybody is reader");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isReader(noteId, userAndRoles);
  }

  public boolean hasRunPermission(Set<String> userAndRoles, String noteId) {
    if (conf.isAnonymousAllowed()) {
      LOGGER.debug("Zeppelin runs in anonymous mode, everybody is reader");
      return true;
    }
    if (userAndRoles == null) {
      return false;
    }
    return isReader(noteId, userAndRoles);
  }

  public boolean isPublic() {
    return conf.isNotebookPublic();
  }

  public void setRoles(String user, Set<String> roles) {
    if (StringUtils.isBlank(user)) {
      LOGGER.warn("Setting roles for empty user");
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

  public void clearPermission(String noteId) {
    notebook.getNote(noteId).setReaders(Sets.newHashSet());
    notebook.getNote(noteId).setRunners(Sets.newHashSet());
    notebook.getNote(noteId).setWriters(Sets.newHashSet());
    notebook.getNote(noteId).setOwners(Sets.newHashSet());
  }
}
