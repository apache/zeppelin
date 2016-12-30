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

package org.apache.zeppelin.interpreter;

import org.apache.zeppelin.scheduler.Job.Status;

import java.util.Date;

/**
 * Remote Zeppelin Server job status
 */
public class RemoteZeppelinJobStatus {

  private String noteId;
  private Status jobStatus;
  private String paragraphId;
  private Date lastRunningTime;

  public String getNoteId() {
    return noteId;
  }

  public void setNoteId(String noteId) {
    this.noteId = noteId;
  }

  public Status getJobStatus() {
    return jobStatus;
  }

  public void setJobStatus(Status jobStatus) {
    this.jobStatus = jobStatus;
  }

  public String getParagraphId() {
    return paragraphId;
  }

  public void setParagraphId(String paragraphId) {
    this.paragraphId = paragraphId;
  }

  public Date getLastRunningTime() {
    return lastRunningTime;
  }

  public void setLastRunningTime(Date lastRunningTime) {
    this.lastRunningTime = lastRunningTime;
  }
}
