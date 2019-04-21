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

import java.io.File;
import java.io.IOException;
import org.apache.zeppelin.background.FileSystemTaskContextStorage;
import org.apache.zeppelin.background.K8sNoteBackgroundTaskManager;
import org.apache.zeppelin.background.NoteBackgroundTask;
import org.apache.zeppelin.background.TaskContext;
import org.apache.zeppelin.background.TaskContextStorage;
import org.apache.zeppelin.conf.ZeppelinConfiguration;

/**
 * Provide TaskContextStorage and creates NoteServingTask.
 */
public class K8sNoteServingTaskManager extends K8sNoteBackgroundTaskManager {

  public K8sNoteServingTaskManager(ZeppelinConfiguration zConf) throws IOException {
    super(zConf);
  }

  @Override
  protected TaskContextStorage createTaskContextStorage() {
    return new FileSystemTaskContextStorage(getConf().getK8sServingContextDir());
  }

  @Override
  protected NoteBackgroundTask createOrGetBackgroundTask(TaskContext taskContext) {
    File servingTemplateDir = new File(getConf().getK8sTemplatesDir(), "background");
    K8sNoteServingTask servingTask = new K8sNoteServingTask(getKubectl(), taskContext, servingTemplateDir);
    return servingTask;
  }
}
