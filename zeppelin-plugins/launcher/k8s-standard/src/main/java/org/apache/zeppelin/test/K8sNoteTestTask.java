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
package org.apache.zeppelin.test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.apache.zeppelin.background.K8sNoteBackgroundTask;
import org.apache.zeppelin.background.TaskContext;
import org.apache.zeppelin.interpreter.launcher.Kubectl;

/**
 * Note test task.
 */
public class K8sNoteTestTask extends K8sNoteBackgroundTask {
  public K8sNoteTestTask(Kubectl kubectl, TaskContext taskContext, File k8sTemplateDir) {
    super(kubectl, taskContext, k8sTemplateDir);
  }

  @Override
  protected Properties getTemplateBindings() throws IOException {
    Properties properties = super.getTemplateBindings();
    String notebookDir = String.format("/zeppelin/task/test/%s/notebook", getTaskContext().getId());
    properties.put("zeppelin.k8s.background.notebook.dir", notebookDir);
    properties.put("zeppelin.k8s.background.autoshutdown", "true");
    properties.put("zeppelin.k8s.background.type", "test");
    return properties;
  }

  @Override
  protected String getResourceName() {
    return String.format("test-%s", getTaskContext().getId());
  }

}
