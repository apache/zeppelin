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
package org.apache.zeppelin.background;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.notebook.Note;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local filesystem implementation of TaskContextStorage.
 *
 * Stores context under
 *
 * <CONTEXT_ROOT>/<context_id>/
 */
public class FileSystemTaskContextStorage implements TaskContextStorage {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemTaskContextStorage.class);
  private final File contextRootDir;

  public FileSystemTaskContextStorage(String contextRoot) {
    contextRootDir = new File(contextRoot);
  }

  @Override
  public void save(TaskContext context) throws IOException {
    delete(context.getId());
    File contextDir = new File(contextRootDir, context.getId());
    File notebookDir = new File(contextDir, "notebook");
    File revFile = new File(contextDir, "rev");
    notebookDir.mkdirs();

    Note note = context.getNote();
    File noteFile = new File(notebookDir, note.getName() + "_" + note.getId() + ".zpln");
    FileUtils.writeStringToFile(noteFile, note.toJson());
    FileUtils.writeStringToFile(revFile, context.getRevId());
  }

  @Override
  public TaskContext load(String taskId) throws IOException {
    File contextDir = new File(contextRootDir, taskId);
    File notebookDir = new File(contextDir, "notebook");
    File revFile = new File(contextDir, "rev");

    if (!contextDir.isDirectory()) {
      return null;
    }

    Collection<File> files = FileUtils.listFiles(notebookDir, new String[]{"zpln"}, false);
    File noteFile = files.iterator().next();
    Note note = Note.fromJson(FileUtils.readFileToString(noteFile));

    String revId = FileUtils.readFileToString(revFile);
    return new TaskContext(note, revId);
  }

  @Override
  public void delete(String taskId) throws IOException {
    File contextDir = new File(contextRootDir, taskId);
    FileUtils.deleteDirectory(contextDir);
  }

  @Override
  public List<TaskContext> list() {
    File[] files = contextRootDir.listFiles();
    if (files == null) {
      return new LinkedList<>();
    }

    return Arrays.stream(files).map(f -> {
      try {
        return load(f.getName());
      } catch (IOException e) {
        LOGGER.error("Can't load task context", e);
        return null;
      }
    }).collect(Collectors.toList());
  }
}
