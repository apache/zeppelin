/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.background;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;

/**
 * Manage note background task. such as test task, serving task.
 */
public abstract class NoteBackgroundTaskManager {
  private final ZeppelinConfiguration zConf;
  BackgroundTaskLifecycleListener listener;

  public NoteBackgroundTaskManager(ZeppelinConfiguration zConf) {
    this.zConf = zConf;
  }

  public NoteBackgroundTask start(Note note, String revId) throws IOException {
    // create task
    TaskContext taskContext = new TaskContext(note, revId);
    getTaskContextStorage().save(taskContext);
    NoteBackgroundTask task = createOrGetBackgroundTask(taskContext);

    // start test
    boolean running = false;
    try {
      running = task.isRunning();
    } catch (IOException e) {
      // task not exists. ignore exception here
    }
    if (!running) {
      task.start();
    }
    return task;
  }

  public NoteBackgroundTask stop(String noteId, String revId) throws IOException {
    NoteBackgroundTask task = get(noteId, revId);
    if (task == null) {
      return null;
    }

    // stop test
    task.stop();

    getTaskContextStorage().delete(task.getTaskContext().getId());
    return task;
  }

  public void delete(String noteId, String revId) throws IOException {
    getTaskContextStorage().delete(TaskContext.getTaskId(noteId, revId));
  }

  public NoteBackgroundTask get(String noteId, String revId) throws IOException {
    TaskContext taskContext = getTaskContextStorage().load(TaskContext.getTaskId(noteId, revId));
    if (taskContext == null) {
      return null;
    }

    NoteBackgroundTask task = createOrGetBackgroundTask(taskContext);
    return task;
  }

  public List<NoteBackgroundTask> list() {
    List<TaskContext> contexts = getTaskContextStorage().list();
    return contexts.stream().map(c -> createOrGetBackgroundTask(c)).collect(Collectors.toList());
  }

  public ZeppelinConfiguration getConf() {
    return zConf;
  }

  protected abstract TaskContextStorage getTaskContextStorage();
  protected abstract NoteBackgroundTask createOrGetBackgroundTask(TaskContext taskContext);

  public BackgroundTaskLifecycleListener getListener() {
    return listener;
  }

  public void setListener(BackgroundTaskLifecycleListener listener) {
    this.listener = listener;
  }
}
