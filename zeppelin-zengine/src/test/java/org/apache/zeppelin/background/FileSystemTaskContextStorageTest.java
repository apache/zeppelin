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

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.background.FileSystemTaskContextStorage;
import org.apache.zeppelin.background.TaskContext;
import org.apache.zeppelin.notebook.Note;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * FileSystemTaskContextStorageTest.
 */
public class FileSystemTaskContextStorageTest {
  private File contextRoot;

  @Before
  public void setUp() {
    contextRoot = Files.createTempDir();
  }

  @After
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(contextRoot);
  }

  @Test
  public void testSaveAndLoad() throws IOException {
    // given
    FileSystemTaskContextStorage contextStorage = new FileSystemTaskContextStorage(contextRoot.getAbsolutePath());
    Note note = new Note();
    TaskContext context = new TaskContext(note, "rev1");

    // when
    contextStorage.save(context);
    TaskContext loaded = contextStorage.load(context.getId());

    // then
    assertEquals(loaded.getId(), context.getId());
    assertEquals(loaded.getRevId(), context.getRevId());
    assertEquals(loaded.getNote(), context.getNote());
  }

  @Test
  public void testSaveOverwrite() throws IOException {
    // given
    FileSystemTaskContextStorage contextStorage = new FileSystemTaskContextStorage(contextRoot.getAbsolutePath());
    Note note = new Note();
    contextStorage.save(new TaskContext(note, "rev1"));

    // when
    note.setName("hello");
    contextStorage.save(new TaskContext(note, "rev1"));
    TaskContext loaded = contextStorage.load(TaskContext.getTaskId(note.getId(), "rev1"));

    // then
    assertEquals("hello", loaded.getNote().getName());
  }

  @Test
  public void testDelete() throws IOException {
    // given
    FileSystemTaskContextStorage contextStorage = new FileSystemTaskContextStorage(contextRoot.getAbsolutePath());
    Note note = new Note();
    TaskContext context = new TaskContext(note, "rev1");
    contextStorage.save(context);
    assertEquals(1, contextStorage.list().size());

    // when
    contextStorage.delete(context.getId());

    // then
    assertEquals(0, contextStorage.list().size());
  }

  @Test
  public void testList() throws IOException {
    // given
    FileSystemTaskContextStorage contextStorage = new FileSystemTaskContextStorage(contextRoot.getAbsolutePath());
    Note note = new Note();
    TaskContext context = new TaskContext(note, "rev1");
    contextStorage.save(context);

    // when
    List<TaskContext> list = contextStorage.list();

    // then
    assertEquals(1, list.size());
    assertEquals("rev1", list.get(0).getRevId());
  }
}
