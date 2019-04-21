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

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.zeppelin.background.K8sNoteBackgroundTask;
import org.apache.zeppelin.background.TaskContext;
import org.apache.zeppelin.background.TaskContextStorage;
import org.apache.zeppelin.interpreter.launcher.Kubectl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Note test task.
 */
public class K8sNoteTestTask extends K8sNoteBackgroundTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(K8sNoteTestTask.class);
  private final String notebookDir;

  public K8sNoteTestTask(Kubectl kubectl,
                         TaskContext taskContext,
                         String notebookDir,
                         File k8sTemplateDir) {
    super(kubectl, taskContext, k8sTemplateDir);
    this.notebookDir = notebookDir;
  }

  @Override
  protected Properties getTemplateBindings() throws IOException {
    Properties properties = super.getTemplateBindings();
    properties.put("zeppelin.k8s.background.notebook.dir", notebookDir);
    properties.put("zeppelin.k8s.background.autoshutdown", "true");
    properties.put("zeppelin.k8s.background.type", "test");
    return properties;
  }

  @Override
  protected String getResourceName() {
    return String.format("test-%s", getTaskContext().getId());
  }

  @Override
  protected String getResourceApiVersion() {
    return "batch/v1";
  }

  @Override
  protected String getResourceType() {
    return "Job";
  }

  @Override
  public Map<String, Object> getInfo() throws IOException {
    Map<String, Object> info = super.getInfo();
    if (info != null) {
      Map<String, Object> status = (Map<String, Object>) info.get("status");
      if (status != null) {
        if (status.containsKey("completionTime")) {
          // job completed. remove job from kubernetes.
          stop();
        }
      }
    }
    return info;
  }

  /**
   * Return false when job does not exists or job is completed.
   * Otherwise return true (on job exists or running)
   * @return
   */
  @Override
  public boolean isRunning() {
    Map<String, Object> resource = null;
    try {
      resource = getInfo();
    } catch (IOException e) {
      LOGGER.error("Can't get task info", e);
      return false;
    }
    if (resource == null) {
      return false;
    }

    if (resource.size() == 0) {
      return true;
    }

    Map<String, Object> status = (Map<String, Object>) resource.get("status");
    if (status == null) {
      return true;
    }

    if (status.containsKey("completionTime")) {
      return false;
    }

    return true;
  }
}
