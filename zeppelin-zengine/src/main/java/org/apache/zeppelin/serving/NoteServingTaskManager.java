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
package org.apache.zeppelin.serving;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;

/**
 * Manage lifecycle of NoteServingTask.
 */
public abstract class NoteServingTaskManager {
  final ZeppelinConfiguration zConf;
  private final TaskContextStorage taskContextStorage;

  public NoteServingTaskManager(ZeppelinConfiguration zConf) {
    this.zConf = zConf;
    this.taskContextStorage = getTaskContextStorage();
  }

  public NoteServingTask start(Note note, String revId) throws IOException {
    // create task
    TaskContext taskContext = new TaskContext(note, revId);
    taskContextStorage.save(taskContext);
    NoteServingTask servingTask = createOrGetServingTask(taskContext);

    // start serving
    servingTask.start();

    return servingTask;
  }

  public NoteServingTask stop(String noteId, String revId) throws IOException {
    NoteServingTask servingTask = get(noteId, revId);
    if (servingTask == null) {
      return null;
    }

    // stop serving
    servingTask.stop();
    return servingTask;
  }

  public void delete(String noteId, String revId) throws IOException {
    taskContextStorage.delete(TaskContext.getTaskId(noteId, revId));
  }

  public NoteServingTask get(String noteId, String revId) throws IOException {
    TaskContext taskContext = taskContextStorage.load(TaskContext.getTaskId(noteId, revId));
    if (taskContext == null) {
      return null;
    }

    NoteServingTask servingTask = createOrGetServingTask(taskContext);
    return servingTask;
  }

  public List<NoteServingTask> list() {
    List<TaskContext> contexts = taskContextStorage.list();
    return contexts.stream().map(c -> createOrGetServingTask(c)).collect(Collectors.toList());
  }

  protected abstract TaskContextStorage getTaskContextStorage();
  protected abstract NoteServingTask createOrGetServingTask(TaskContext taskContext);
}
