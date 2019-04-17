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
import java.util.Properties;
import org.apache.zeppelin.interpreter.launcher.Kubectl;

/**
 * Start / Stop / Monitor serving task.
 */
public class K8sNoteServingTask implements NoteServingTask {
  private final Kubectl kubectl;
  private final TaskContext taskContext;
  private final File k8sTemplateDir;

  public K8sNoteServingTask(Kubectl kubectl, TaskContext taskContext, File k8sTemplateDir) {
    this.kubectl = kubectl;
    this.taskContext = taskContext;
    this.k8sTemplateDir = k8sTemplateDir;
  }

  @Override
  public void start() throws IOException {
    kubectl.apply(k8sTemplateDir, getTemplateBindings(), false);
  }

  Properties getTemplateBindings() throws IOException {
    Properties k8sProperties = new Properties();
    String taskId = taskContext.getId();
    String servingName = String.format("serving-%s", taskId);

    // k8s template properties
    k8sProperties.put("zeppelin.k8s.serving.namespace", kubectl.getNamespace());
    k8sProperties.put("zeppelin.k8s.serving.name", servingName);
    k8sProperties.put("zeppelin.k8s.serving.noteId", taskContext.getNote().getId());
    k8sProperties.put("zeppelin.k8s.serving.serviceContext", "");

    // interpreter properties overrides the values
    return k8sProperties;
  }

  @Override
  public void stop() throws IOException {
    kubectl.apply(k8sTemplateDir, getTemplateBindings(), true);
  }

  @Override
  public boolean isRunning() {
    return false;
  }
}
