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
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Notebook repository (persistence layer) abstraction
 */
public interface NotebookRepo {

  void init(ZeppelinConfiguration zConf) throws IOException;

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

}
