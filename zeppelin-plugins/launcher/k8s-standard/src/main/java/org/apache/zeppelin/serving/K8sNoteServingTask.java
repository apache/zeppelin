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
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.launcher.Kubectl;

/**
 * Start / Stop / Monitor serving task.
 */
public class K8sNoteServingTask extends NoteServingTask {
  private final Kubectl kubectl;
  private final File k8sTemplateDir;
  private final Gson gson = new Gson();

  public K8sNoteServingTask(Kubectl kubectl, TaskContext taskContext, File k8sTemplateDir) {
    super(taskContext);
    this.kubectl = kubectl;
    this.k8sTemplateDir = k8sTemplateDir;
  }

  @Override
  public void start() throws IOException {
    kubectl.apply(k8sTemplateDir, getTemplateBindings(), false);
  }

  Properties getTemplateBindings() throws IOException {
    TaskContext taskContext = getTaskContext();
    Properties k8sProperties = new Properties();
    String taskId = taskContext.getId();
    String servingName = getServingName();
    String notebookDir = String.format("/zeppelin/task/serving/%s/notebook", taskContext.getId());

    // k8s template properties
    k8sProperties.put("zeppelin.k8s.serving.taskId", taskId);
    k8sProperties.put("zeppelin.k8s.serving.namespace", kubectl.getNamespace());
    k8sProperties.put("zeppelin.k8s.serving.name", servingName);
    k8sProperties.put("zeppelin.k8s.serving.notebook.dir", notebookDir);
    k8sProperties.put("zeppelin.k8s.serving.noteId", taskContext.getNote().getId());
    k8sProperties.put("zeppelin.k8s.serving.serviceContext", "");

    // interpreter properties overrides the values
    return k8sProperties;
  }

  private String getServingName() {
    return String.format("serving-%s", getTaskContext().getId());
  }

  @Override
  public void stop() throws IOException {
    kubectl.apply(k8sTemplateDir, getTemplateBindings(), true);

  }

  @Override
  public boolean isRunning() throws IOException {
    String podString = kubectl.getByLabel("pod", String.format("app=%s", getServingName()));
    if (StringUtils.isEmpty(podString)) {
      return false;
    }

    Map<String, Object> pod = gson.fromJson(podString, new TypeToken<Map<String, Object>>() {
    }.getType());
    List<Map<String, Object>> items = (List<Map<String, Object>>) pod.get("items");
    if (items == null) {
      return false;
    }

    Iterator<Map<String, Object>> it = items.iterator();
    while (it.hasNext()) {
      Map<String, Object> item = it.next();
      Map<String, Object> status = (Map<String, Object>) item.get("status");
      if (status == null) {
        return false;
      }

      String phase = (String) status.get("phase");
      if (phase == null) {
        return false;
      }

      return phase.equals("Running");
    }

    return false;
  }
}
