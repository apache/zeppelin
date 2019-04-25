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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.background.K8sNoteBackgroundTask;
import org.apache.zeppelin.background.NoteBackgroundTask;
import org.apache.zeppelin.background.TaskContext;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.launcher.Kubectl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Start / Stop / Monitor serving task.
 */
public class K8sNoteServingTask extends K8sNoteBackgroundTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(K8sNoteServingTask.class);
  private final String notebookDir;
  private final Gson gson = new Gson();

  public K8sNoteServingTask(Kubectl kubectl,
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
    properties.put("zeppelin.k8s.background.autoshutdown", "false");
    properties.put("zeppelin.k8s.background.type", "serving");
    properties.put("zeppelin.k8s.serving.metric.redis.addr",
            System.getenv(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_METRIC_REDIS_ADDR.name()));
    return properties;
  }

  @Override
  protected String getResourceName() {
    return String.format("serving-%s", getTaskContext().getId());
  }

  @Override
  protected String getResourceApiVersion() {
    return "apps/v1";
  }

  @Override
  protected String getResourceType() {
    return "Deployment";
  }

  public Map<String, Object> getInfo() throws IOException {
    HashMap<String, Object> combinedInfo = new HashMap<String, Object>();
    Map<String, Object> info = super.getInfo();

    if (info != null) {
      combinedInfo.putAll(info);

      Kubectl kubectl = getKubectl();
      TaskContext context = getTaskContext();
      String labelFilter = String.format("serving=true,noteId=%s,revId=%s",
              context.getNote().getId(),
              context.getRevId());
      String resourceJsonString = kubectl.getByLabel("service", labelFilter);

      HashMap<String, String> endPointServiceNameMap = new HashMap<String, String>();

      if (!StringUtils.isEmpty(resourceJsonString)) {
        Map<String, Object> services = gson.fromJson(resourceJsonString,
                new TypeToken<Map<String, Object>>() {
                }.getType());

        if (services.containsKey("items")) {
          List<Map<String, Object>> items = (List<Map<String, Object>>) services.get("items");
          for (Map<String, Object> item : items) {
            Map<String, Object> metadata = (Map<String, Object>) item.get("metadata");
            Map<String, Object> labels = (Map<String, Object>) metadata.get("labels");
            for (String labelkey : labels.keySet()) {
              if (labelkey.startsWith("endpoint-")) {
                endPointServiceNameMap.put((String) labels.get(labelkey), (String) metadata.get("name"));
              }
            }
          }
        }
      }

      combinedInfo.put("endpoints", endPointServiceNameMap);
    }
    return combinedInfo;
  }

  @Override
  public boolean isRunning() {
    Map<String, Object> resource = null;
    try {
      resource = getInfo();
    } catch (IOException e) {
      // does not exists
      return false;
    }
    if (resource == null) {
      return false;
    }

    if (resource.size() == 0) {
      return false;
    }

    Map<String, Object> status = (Map<String, Object>) resource.get("status");
    if (status == null) {
      return false;
    }

    if (!status.containsKey("availableReplicas") || status.get("availableReplicas") == null) {
      return false;
    }

    return (int) status.get("availableReplicas") > 0;
  }
}
