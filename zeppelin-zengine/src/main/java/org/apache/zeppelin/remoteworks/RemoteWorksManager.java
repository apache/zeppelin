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

package org.apache.zeppelin.remoteworks;

import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.RemoteWorksController;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * Zeppelin Server RemoteWorkController Singleton.
 */
public class RemoteWorksManager {
  private static final Logger LOG = LoggerFactory.getLogger(RemoteWorksManager.class);
  private static NotebookJobManager instance;

  public RemoteWorksManager(Notebook notebook) {
    if (RemoteWorksManager.instance == null) {
      RemoteWorksManager.instance = new NotebookJobManager(notebook);
    }
  }

  public static NotebookJobManager getInstance() {
    return RemoteWorksManager.instance;
  }

  private class NotebookJobManager implements RemoteWorksController {
    private transient Notebook notebook;

    public NotebookJobManager(Notebook notebook) {
      setNotebook(notebook);
    }

    private void setNotebook(Notebook notebook) {
      this.notebook = notebook;
    }

    private Notebook getNotebook() throws NullPointerException {
      if (notebook == null) {
        throw new NullPointerException("Notebook instance is Null");
      }
      return notebook;
    }

    public List<InterpreterContextRunner> getRemoteContextRunner(String noteId) {
      return getRemoteContextRunner(noteId, null);
    }

    public List<InterpreterContextRunner> getRemoteContextRunner(
        String noteId, String paragraphId) {
      List<InterpreterContextRunner> runner = new LinkedList<>();
      try {
        Note note = getNotebook().getNote(noteId);
        if (note != null) {
          if (paragraphId != null) {
            Paragraph paragraph = note.getParagraph(paragraphId);
            if (paragraph != null) {
              runner.add(paragraph.getInterpreterContextRunner());
            }
          } else {
            for (Paragraph p : note.getParagraphs()) {
              runner.add(p.getInterpreterContextRunner());
            }
          }
        }
      } catch (NullPointerException e) {
        LOG.warn(e.getMessage());
      }
      return runner;
    }

  }

}
