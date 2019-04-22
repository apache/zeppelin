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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.zeppelin.background.BackgroundTaskLifecycleListener;
import org.apache.zeppelin.background.FileSystemTaskContextStorage;
import org.apache.zeppelin.background.K8sNoteBackgroundTaskManager;
import org.apache.zeppelin.background.NoteBackgroundTask;
import org.apache.zeppelin.background.TaskContext;
import org.apache.zeppelin.background.TaskContextStorage;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.launcher.BackgroundTaskLifecycleWatcherImpl;
import org.apache.zeppelin.interpreter.launcher.Kubectl;
import org.apache.zeppelin.notebook.Note;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide TaskContextStorage and creates NoteServingTask.
 */
public class K8sNoteServingTaskManager extends K8sNoteBackgroundTaskManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(K8sNoteServingTaskManager.class);
  private final BackgroundTaskLifecycleWatcherImpl<Deployment> watcher;
  Gson gson = new Gson();

  public K8sNoteServingTaskManager(ZeppelinConfiguration zConf) throws IOException {
    super(zConf);

    watcher = new BackgroundTaskLifecycleWatcherImpl<Deployment>(getListener()) {
      @Override
      protected String getTaskId(Deployment deployment) {
        return deployment.getMetadata().getName().replaceFirst("serving-", "");
      }
    };

    getKubectl().watchDeployments(watcher, "taskType",  "serving");
  }

  @Override
  protected TaskContextStorage createTaskContextStorage() {
    return new FileSystemTaskContextStorage(getConf().getK8sServingContextDir());
  }

  @Override
  protected NoteBackgroundTask createOrGetBackgroundTask(TaskContext taskContext) {
    File servingTemplateDir = new File(getConf().getK8sTemplatesDir(), "background");
    K8sNoteServingTask servingTask = new K8sNoteServingTask(
            getKubectl(),
            taskContext,
            String.format("%s/%s/notebook",
                    new File(getConf().getK8sServingContextDir()).getAbsolutePath(),
                    taskContext.getId()),
            servingTemplateDir);
    return servingTask;
  }

  @Override
  public List<NoteBackgroundTask> list() {
    Kubectl kubectl = getKubectl();
    Map deployments = null;
    List<NoteBackgroundTask> tasks = new LinkedList<>();

    try {
      String deploymentJsonString = kubectl.getByLabel("deployment", "taskType=serving");
      deployments = gson.fromJson(deploymentJsonString, new TypeToken<Map>() {}.getType());
    } catch (IOException e) {
      LOGGER.error("Error", e);
      return tasks;
    }

    List<Map> items = (List<Map>) deployments.get("items");
    if (items == null) {
      return tasks;
    }

    for (Map item : items) {
      Map metadata = (Map) item.get("metadata");

      // name format is "serving-<noteId>-<revId>"
      String name = (String) metadata.get("name");
      String[] tokens = name.split("-");
      if (tokens == null || tokens.length != 3) {
        LOGGER.warn("Unrecognized serving name {}", name);
        continue;
      }

      String noteId = tokens[1].toUpperCase();
      String revId = tokens[2];

      TaskContext context = null;
      try {
        context = getTaskContextStorage().load(TaskContext.getTaskId(noteId, revId));
      } catch (IOException e) {
        LOGGER.warn("Serving {} does not exists in task storage", name);
        context = null;
      }

      if (context == null) {
        // serving exists, but no info in task context.
        // create empty note. so at least we can stop it.
        Note note = new Note();
        note.setName("empty");
        note.setId(noteId);
        context = new TaskContext(note, revId);
      }

      tasks.add(createOrGetBackgroundTask(context));
    }

    return tasks;
  }

  public void setListener(BackgroundTaskLifecycleListener listener) {
    super.setListener(listener);
    watcher.setListener(listener);
  }
}
