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

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.background.FileSystemTaskContextStorage;
import org.apache.zeppelin.background.TaskContext;
import org.apache.zeppelin.interpreter.launcher.Kubectl;
import org.apache.zeppelin.notebook.Note;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;

/**
 * K8sNoteServingTaskTest
 */
public class K8sNoteServingTaskTest {
  private File contextRoot;
  private FileSystemTaskContextStorage storage;

  @Before
  public void setUp() {
    contextRoot = Files.createTempDir();
  }

  @After
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(contextRoot);
  }

  @Test
  public void testStart() throws IOException {
    Note note = new Note();
    note.setId("2E63B9RE6");
    TaskContext task = new TaskContext(note, "rev1");

    storage = new FileSystemTaskContextStorage(contextRoot.getAbsolutePath());
    storage.save(task);

    Kubectl kubectl = mock(Kubectl.class);
    kubectl.setNamespace("default");
    File servingTemplate = new File("../../../k8s/serving");
    System.out.println(servingTemplate.getAbsolutePath());
    K8sNoteServingTask noteServingTask = new K8sNoteServingTask(
            kubectl,
            task,
            String.format("%s/%s/notebook", contextRoot.getAbsolutePath(), task.getId()),
            servingTemplate);
    noteServingTask.start();
  }

  @Test
  public void testStop() throws IOException {
    Note note = new Note();
    note.setId("2E63B9RE6");
    TaskContext task = new TaskContext(note, "rev1");

    storage = new FileSystemTaskContextStorage(contextRoot.getAbsolutePath());

    Kubectl kubectl = new Kubectl("kubectl");
    kubectl.setNamespace("default");
    File servingTemplate = new File("../../../k8s/serving");
    K8sNoteServingTask noteServingTask = new K8sNoteServingTask(
            kubectl,
            task,
            String.format("%s/%s/notebook", contextRoot.getAbsolutePath(), task.getId()),
            servingTemplate);
    noteServingTask.stop();
  }

  @Test
  public void testIsRunning() throws IOException {
    Note note = new Note();
    note.setId("2E63B9RE6");
    TaskContext task = new TaskContext(note, "rev1");

    storage = new FileSystemTaskContextStorage(contextRoot.getAbsolutePath());

    Kubectl kubectl = new Kubectl("kubectl");
    kubectl.setNamespace("default");
    File servingTemplate = new File("../../../k8s/serving");
    K8sNoteServingTask noteServingTask = new K8sNoteServingTask(
            kubectl,
            task,
            String.format("%s/%s/notebook", contextRoot.getAbsolutePath(), task.getId()),
            servingTemplate);

    boolean isRunning = noteServingTask.isRunning();
  }
}
