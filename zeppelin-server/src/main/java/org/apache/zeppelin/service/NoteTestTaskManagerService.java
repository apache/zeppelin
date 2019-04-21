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
package org.apache.zeppelin.service;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.zeppelin.background.NoteBackgroundTask;
import org.apache.zeppelin.background.NoteBackgroundTaskManager;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.plugin.PluginManager;

import javax.inject.Inject;

public class NoteTestTaskManagerService {
  private final ZeppelinConfiguration zConf;
  private final NotebookService notebookService;
  private final NoteBackgroundTaskManager testTaskManager;

  @Inject
  public NoteTestTaskManagerService(ZeppelinConfiguration zConf,
                                    NotebookService notebookService) throws IOException {
    this.zConf = zConf;
    this.notebookService = notebookService;

    PluginManager pluginManager = PluginManager.get();
    testTaskManager = pluginManager.loadNoteTestTaskManager();
  }

  public NoteBackgroundTask startTest(String noteId, String revId, ServiceContext serviceContext) throws Exception {
    final AtomicReference<Note> noteRef = new AtomicReference<>();
    final AtomicReference<Exception> exRef = new AtomicReference<>();

    notebookService.getNotebyRevision(noteId, revId, serviceContext, new ServiceCallback<Note>() {
      @Override
      public void onStart(String message, ServiceContext context) throws IOException {

      }

      @Override
      public void onSuccess(Note result, ServiceContext context) throws IOException {
        noteRef.set(result);
        synchronized (noteRef) {
          noteRef.notify();
        }
      }

      @Override
      public void onFailure(Exception ex, ServiceContext context) throws IOException {
        exRef.set(ex);
        synchronized (noteRef) {
          noteRef.notify();
        }
      }
    });

    synchronized (noteRef) {
      while (noteRef.get() == null && exRef.get() == null) {
        noteRef.wait(100);
      }

      if (exRef.get() != null) {
        throw exRef.get();
      }

      Note note = noteRef.get();
      NoteBackgroundTask testTask = testTaskManager.start(note, revId);
      return testTask;
    }
  }

  public NoteBackgroundTask stopTest(String noteId, String revId, ServiceContext serviceContext) throws Exception {
    // TODO check permission
    return testTaskManager.stop(noteId, revId);
  }

  public NoteBackgroundTask getTest(String noteId, String revId, ServiceContext serviceContext) throws IOException {
    return testTaskManager.get(noteId, revId);
  }
}
