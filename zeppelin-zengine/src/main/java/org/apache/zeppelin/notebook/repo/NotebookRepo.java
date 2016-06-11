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

import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Notebook repository (persistence layer) abstraction
 */
public interface NotebookRepo {
  @ZeppelinApi public List<NoteInfo> list(AuthenticationInfo subject) throws IOException;
  @ZeppelinApi public Note get(String noteId, AuthenticationInfo subject) throws IOException;
  @ZeppelinApi public void save(Note note, AuthenticationInfo subject) throws IOException;
  @ZeppelinApi public void remove(String noteId, AuthenticationInfo subject) throws IOException;

  /**
   * Release any underlying resources
   */
  @ZeppelinApi public void close();

  /**
   * Versioning API
   */

  /**
   * chekpoint (set revision) for notebook.
   * @param noteId Id of the Notebook
   * @param checkpointMsg message description of the checkpoint
   * @return Rev
   * @throws IOException
   */
  @ZeppelinApi
  public Revision checkpoint(String noteId, String checkpointMsg, AuthenticationInfo subject)
      throws IOException;

  /**
   * Get particular revision of the Notebook.
   * 
   * @param noteId Id of the Notebook
   * @param rev revision of the Notebook
   * @return a Notebook
   * @throws IOException
   */
  @ZeppelinApi public Note get(String noteId, Revision rev) throws IOException;

  /**
   * List of revisions of the given Notebook.
   * 
   * @param noteId id of the Notebook
   * @return list of revisions
   */
  @ZeppelinApi public List<Revision> revisionHistory(String noteId);

  /**
   * Represents the 'Revision' a point in life of the notebook
   */
  static class Revision {
    public Revision(String revId, String message, int time) {
      this.revId = revId;
      this.message = message;
      this.time = time;
    }
    public String revId;
    public String message;
    public int time;
  }

}
