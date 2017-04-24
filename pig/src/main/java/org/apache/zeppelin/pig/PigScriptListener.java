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


package org.apache.zeppelin.pig;

import org.apache.pig.impl.plan.OperatorPlan;
import org.apache.pig.tools.pigstats.JobStats;
import org.apache.pig.tools.pigstats.OutputStats;
import org.apache.pig.tools.pigstats.PigProgressNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class PigScriptListener implements PigProgressNotificationListener {

  private static Logger LOGGER = LoggerFactory.getLogger(PigScriptListener.class);

  private Set<String> jobIds = new HashSet();
  private int progress;

  @Override
  public void initialPlanNotification(String scriptId, OperatorPlan<?> plan) {

  }

  @Override
  public void launchStartedNotification(String scriptId, int numJobsToLaunch) {

  }

  @Override
  public void jobsSubmittedNotification(String scriptId, int numJobsSubmitted) {

  }

  @Override
  public void jobStartedNotification(String scriptId, String assignedJobId) {
    this.jobIds.add(assignedJobId);
  }

  @Override
  public void jobFinishedNotification(String scriptId, JobStats jobStats) {

  }

  @Override
  public void jobFailedNotification(String scriptId, JobStats jobStats) {

  }

  @Override
  public void outputCompletedNotification(String scriptId, OutputStats outputStats) {

  }

  @Override
  public void progressUpdatedNotification(String scriptId, int progress) {
    LOGGER.debug("scriptId:" + scriptId + ", progress:" + progress);
    this.progress = progress;
  }

  @Override
  public void launchCompletedNotification(String scriptId, int numJobsSucceeded) {

  }

  public Set<String> getJobIds() {
    return jobIds;
  }

  public int getProgress() {
    return progress;
  }
}
