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


package org.apache.zeppelin.client;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Execution result of each statement.
 *
 */
public class ExecuteResult {

  private String statementId;
  private Status status;
  private List<Result> results;
  private List<String> jobUrls;
  private List<Result> bufferedResults;
  private int progress;

  public ExecuteResult(ParagraphResult paragraphResult) {
    this.statementId = paragraphResult.getParagraphId();
    this.status = paragraphResult.getStatus();
    this.progress = paragraphResult.getProgress();
    this.results = paragraphResult.getResults();
    this.jobUrls = paragraphResult.getJobUrls();
  }

  public String getStatementId() {
    return statementId;
  }

  public Status getStatus() {
    return status;
  }

  public List<Result> getResults() {
    return results;
  }

  public List<String> getJobUrls() {
    return jobUrls;
  }

  public int getProgress() {
    return progress;
  }

  @Override
  public String toString() {
    return "ExecuteResult{" +
            "status=" + status +
            ", progress=" + progress +
            ", results=" + StringUtils.join(results, ", ") +
            ", jobUrls=" + jobUrls +
            '}';
  }
}
