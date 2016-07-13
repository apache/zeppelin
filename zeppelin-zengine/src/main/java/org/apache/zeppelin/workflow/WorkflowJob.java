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

import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * workflow job object
 *
 */
public class WorkflowJob {
  static Logger logger = LoggerFactory.getLogger(WorkflowManager.class);
  private String workflowId;
  private WorkflowJobItem workflowJob;

  public WorkflowJob() {
    this.workflowJob = null;
    this.workflowId = String.valueOf(this.hashCode());
  }

  public WorkflowJob(String workflowId) {
    this.workflowJob = null;
    this.workflowId = workflowId;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowJobItem(WorkflowJobItem workflowItem) {
    if (this.workflowJob == null) {
      this.workflowId = workflowItem.getNotebookId();
    }
    this.workflowJob = workflowItem;
  }

  public WorkflowJobItem getWorkflowJobItemFirst() {
    return this.workflowJob;
  }

  public WorkflowJobItem getWorkflowJobItemLast() {
    WorkflowJobItem currentJobItem = this.workflowJob;
    WorkflowJobItem lastJobItem = null;

    while (currentJobItem != null) {
      lastJobItem = currentJobItem;
      currentJobItem = currentJobItem.getOnSuccessJob();
    }
    return lastJobItem;
  }

  public void removeWorkflowJobItemLast() {
    List<WorkflowJobItem> seqJobList = new LinkedList<>();
    WorkflowJobItem currentJobItem = this.workflowJob;
    WorkflowJobItem lastJobItem = null;

    while (currentJobItem != null) {
      seqJobList.add(currentJobItem);
      currentJobItem = currentJobItem.getOnSuccessJob();
    }

    int workflowJobItemListSize = seqJobList.size();
    if (workflowJobItemListSize > 0) {
      lastJobItem = seqJobList.get(workflowJobItemListSize - 1);
      lastJobItem.setOnSuccessJob(null);
    }

  }

  public WorkflowJobItem getWorkflowJobItemTarget(String notebookId, String paragraphId) {
    if (this.workflowJob == null) {
      return null;
    }

    return this.workflowJob.getFindJob(notebookId, paragraphId);
  }

  public WorkflowJobItem getIfNextJob(String finishiedNotebookId, String finishiedParagraphId) {
    return this.workflowJob.getIfNextJob(finishiedNotebookId, finishiedParagraphId);
  }

  public void notifyJobWork(
      Job.Status status, String finishedNotebookId, String finishedParagraphId) {
    if (this.workflowJob != null) {
      this.workflowJob.notifyJobFinishied(status, finishedNotebookId, finishedParagraphId);
    }
  }

}
