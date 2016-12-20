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

package org.apache.zeppelin.notebook.repo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Notebook repository (persistence layer) abstraction
 */
public interface NotebookRepo {
  /**
   * Lists notebook information about all notebooks in storage.
   * @param subject contains user information.
   * @return
   * @throws IOException
   */
  @ZeppelinApi public List<NoteInfo> list(AuthenticationInfo subject) throws IOException;

  /**
   * Get the notebook with the given id.
   * @param noteId is note id.
   * @param subject contains user information.
   * @return
   * @throws IOException
   */
  @ZeppelinApi public Note get(String noteId, AuthenticationInfo subject) throws IOException;

  /**
   * Save given note in storage
   * @param note is the note itself.
   * @param subject contains user information.
   * @throws IOException
   */
  @ZeppelinApi public void save(Note note, AuthenticationInfo subject) throws IOException;

  /**
   * Remove note with given id.
   * @param noteId is the note id.
   * @param subject contains user information.
   * @throws IOException
   */
  @ZeppelinApi public void remove(String noteId, AuthenticationInfo subject) throws IOException;

  /**
   * Release any underlying resources
   */
  @ZeppelinApi public void close();

  /**
   * Versioning API (optional, preferred to have).
   */

  /**
   * chekpoint (set revision) for notebook.
   * @param noteId Id of the Notebook
   * @param checkpointMsg message description of the checkpoint
   * @return Rev
   * @throws IOException
   */
  @ZeppelinApi public Revision checkpoint(String noteId, String checkpointMsg, 
      AuthenticationInfo subject) throws IOException;

  /**
   * Get particular revision of the Notebook.
   * 
   * @param noteId Id of the Notebook
   * @param rev revision of the Notebook
   * @return a Notebook
   * @throws IOException
   */
  @ZeppelinApi public Note get(String noteId, String revId, AuthenticationInfo subject)
      throws IOException;

  /**
   * List of revisions of the given Notebook.
   * 
   * @param noteId id of the Notebook
   * @return list of revisions
   */
  @ZeppelinApi public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject);

  /**
   * Set note to particular revision.
   * 
   * @param noteId Id of the Notebook
   * @param rev revision of the Notebook
   * @return a Notebook
   * @throws IOException
   */
  @ZeppelinApi
  public Note setNoteRevision(String noteId, String revId, AuthenticationInfo subject)
      throws IOException;

  /**
   * Get NotebookRepo settings got the given user.
   *
   * @param subject
   * @return
   */
  @ZeppelinApi public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject);

  /**
   * update notebook repo settings.
   *
   * @param settings
   * @param subject
   */
  @ZeppelinApi public void updateSettings(Map<String, String> settings, AuthenticationInfo subject);

  /**
   * Represents the 'Revision' a point in life of the notebook
   */
  static class Revision {
    public static final Revision EMPTY = new Revision(StringUtils.EMPTY, StringUtils.EMPTY, 0);
    
    public String id;
    public String message;
    public int time;
    
    public Revision(String revId, String message, int time) {
      this.id = revId;
      this.message = message;
      this.time = time;
    }

    public static boolean isEmpty(Revision revision) {
      return revision == null || EMPTY.equals(revision);
    }
  }

}
