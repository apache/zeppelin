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

package org.apache.zeppelin.workflow;

import org.apache.zeppelin.scheduler.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * workflow manager
 *
 */
public class WorkflowManager {
  static Logger logger = LoggerFactory.getLogger(WorkflowManager.class);
  private Map<String, WorkflowJob> workflowJobLists;

  public WorkflowManager() {
    this.workflowJobLists = new HashMap<>();
  }

  public void setWorkflow(
      String workflowId, String jobRootNotebookId, String jobRootParagraphId) {
    if (workflowJobLists.containsKey(workflowId) == true) {
      workflowJobLists.remove(workflowId);
    }
    WorkflowJob newWorkflow = new WorkflowJob(workflowId);
    WorkflowJobItem jobRootItem = new WorkflowJobItem(jobRootNotebookId, jobRootParagraphId);
    newWorkflow.setWorkflowJobItem(jobRootItem);
    workflowJobLists.put(workflowId, newWorkflow);
  }

  public Map<String, WorkflowJob> getWorkflow() {
    return workflowJobLists;
  }

  public WorkflowJob getWorkflow(String workflowId) {
    return workflowJobLists.get(workflowId);
  }

  public void setJobNotify(Job.Status jobStatus, String notebookId, String paragraphId) {
    for (WorkflowJob job : workflowJobLists.values()) {
      synchronized (job) {
        job.notifyJobWork(jobStatus, notebookId, paragraphId);
      }
    }
  }

  public Map<String, List<String>> getNextJob(String notebookId, String paragraphId) {
    Map<String, List<String>> nextJobList = new HashMap<>();

    for (WorkflowJob job : workflowJobLists.values()) {
      synchronized (job) {
        WorkflowJobItem nextJob = job.getIfNextJob(notebookId, paragraphId);
        if (nextJob != null) {
          LinkedList<String> paragraphList = new LinkedList<String>();
          paragraphList.add(nextJob.getParagaraphId());
          nextJobList.put(nextJob.getNotebookId(), paragraphList);
        }
      }
    }

    return nextJobList;
  }
}
