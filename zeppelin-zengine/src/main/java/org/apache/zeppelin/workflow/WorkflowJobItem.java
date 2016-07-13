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
import org.apache.zeppelin.scheduler.Job.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.attribute.standard.JobState;

/**
 * workflow job item
 *
 */
public class WorkflowJobItem {
  static Logger logger = LoggerFactory.getLogger(WorkflowJobItem.class);
  /**
   * workflow job Status enum
   *
   */
  public enum WorkflowStatus {
    WAIT, PROGRESS, ERROR, SUCCESS, ABORT
  }

  private String notebookId;
  private String paragaraphId;
  private WorkflowJobItem onSuccessJob;
  private WorkflowStatus status;

  public WorkflowJobItem() {
    this.onSuccessJob = null;
    this.notebookId = null;
    this.paragaraphId = null;
    this.status = WorkflowStatus.WAIT;
  }

  public WorkflowJobItem(String notebookId, String paragaraphId) {
    this();
    this.notebookId = notebookId;
    this.paragaraphId = paragaraphId;
  }

  public String getNotebookId() {
    return notebookId;
  }

  public void setNotebookId(String notebookId) {
    this.notebookId = notebookId;
  }

  public String getParagaraphId() {
    return paragaraphId;
  }

  public void setParagaraphId(String paragaraphId) {
    this.paragaraphId = paragaraphId;
  }

  public WorkflowStatus getStatus() {
    return status;
  }

  public void setStatus(WorkflowStatus status) {
    this.status = status;
  }

  public WorkflowJobItem getOnSuccessJob() {
    return onSuccessJob;
  }

  public void setOnSuccessJob(WorkflowJobItem onSuccessJob) {
    this.onSuccessJob = onSuccessJob;
  }

  public WorkflowJobItem getFindJob(String notebookId, String paragaraphId) {
    if (isMyJob(notebookId, paragaraphId) == true) {
      return this;
    }

    if (this.onSuccessJob != null) {
      return this.onSuccessJob.getFindJob(notebookId, paragaraphId);
    } else {
      return null;
    }
  }

  public WorkflowJobItem getIfNextJob(String finishedNotebookId, String finishedParagraphId) {
    String childNotebookId = null;
    String childParagraphId = null;
    if (getStatus() == WorkflowStatus.SUCCESS) {
      if (this.onSuccessJob != null) {
        childNotebookId = this.onSuccessJob.getNotebookId();
        childParagraphId = this.onSuccessJob.getParagaraphId();
        return this.onSuccessJob.getIfNextJob(childNotebookId, childParagraphId);
      }
    }

    if (getStatus() == WorkflowStatus.WAIT) {
      if (isMyJob(finishedNotebookId, finishedParagraphId)) {
        return this;
      }
    }

    return null;
  }

  public void notifyJobFinishied(
      Status status, String finishedNotebookId, String finishedParagraphId) {

    if (isMyJob(finishedNotebookId, finishedParagraphId) &&
        (getStatus() == WorkflowStatus.WAIT || getStatus() == WorkflowStatus.PROGRESS)) {
      if (Status.ABORT == status) {
        setStatus(WorkflowStatus.ABORT);
      } else if (Status.ERROR == status) {
        setStatus(WorkflowStatus.ERROR);
      } else if (Status.FINISHED == status) {
        setStatus(WorkflowStatus.SUCCESS);
      } else if (Status.RUNNING == status) {
        setStatus(WorkflowStatus.PROGRESS);
      }
    } else {
      if (this.onSuccessJob != null) {
        this.onSuccessJob.notifyJobFinishied(status, finishedNotebookId, finishedParagraphId);
      }
    }

  }

  private boolean isMyJob(String finishedNotebookId, String finishedParagraphId) {
    if (getNotebookId().equals(finishedNotebookId) &&
      getParagaraphId().equals(finishedParagraphId)) {
      return true;
    }
    return false;
  }
}
