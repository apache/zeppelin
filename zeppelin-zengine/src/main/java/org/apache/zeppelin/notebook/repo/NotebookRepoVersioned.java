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

import org.apache.zeppelin.notebook.Note;

/**
 * Notebook repository w/ versions
 */
public interface NotebookRepoVersioned extends NotebookRepo {

  /**
   * Get particular revision of the Notebooks
   *
   * @param noteId Id of the Notebook
   * @param rev revision of the Notebook
   * @return a Notebook
   * @throws IOException
   */
  public Note get(String noteId, String rev) throws IOException;

  /**
   * List of revisions of the given Notebook
   *
   * @param noteId id of the Notebook
   * @return list of revisions
   */
  public List<Rev> history(String noteId);

  /**
   * Represents the 'Revision' a point in life of the notebook
   */
  static class Rev {
    public Rev(String name, int time) {
      this.name = name;
      this.time = time;
    }
    String name;
    int time;
  }

}
