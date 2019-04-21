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

import java.io.IOException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.launcher.Kubectl;

/**
 * Manage note test task.
 */
public abstract class K8sNoteBackgroundTaskManager extends NoteBackgroundTaskManager {

  private TaskContextStorage taskContextStorage = null;
  private final Kubectl kubectl;

  public K8sNoteBackgroundTaskManager(ZeppelinConfiguration zConf) throws IOException {
    super(zConf);
    kubectl = new Kubectl(zConf.getK8sKubectlCmd());
    kubectl.setNamespace(Kubectl.getNamespaceFromContainer());
  }

  protected abstract TaskContextStorage createTaskContextStorage();

  @Override
  protected synchronized TaskContextStorage getTaskContextStorage() {
    if (taskContextStorage == null) {
      taskContextStorage = createTaskContextStorage();
    }
    return taskContextStorage;
  }

  protected Kubectl getKubectl() {
    return kubectl;
  }
}
