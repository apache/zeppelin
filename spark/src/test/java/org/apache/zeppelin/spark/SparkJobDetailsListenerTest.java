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

package org.apache.zeppelin.spark;

import static org.junit.Assert.*;

import java.util.Properties;

import org.apache.spark.scheduler.SparkListenerJobStart;
import org.apache.spark.scheduler.StageInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SparkJobDetailsListenerTest {

  @Test
  public void constructCorrectJobURL() {
    StageInfo[] stageInfo = new StageInfo[] {ListenerTestHelper.stageInfo(10),
      ListenerTestHelper.stageInfo(5), ListenerTestHelper.stageInfo(7)};
    SparkListenerJobStart jobStart = ListenerTestHelper.listenerJobStart(1, stageInfo);
    SparkListenerJob listenerJob = new SparkListenerJob(jobStart, "http://172.17.0.2:4040");

    assertEquals(listenerJob.getJobUIAddress(), "http://172.17.0.2:4040/jobs/job/?id=1");
    assertEquals(listenerJob.getJobId(), 1);
    assertArrayEquals(listenerJob.getStageIds(), new int[] {10, 5, 7});
    assertEquals(listenerJob.getJobName(), "Job 1");
    // stages should be reported in sorted order
    assertEquals(listenerJob.getJobDescription(), "Stages [5, 7, 10]");
  }

  @Test
  public void processJobStartAsNull() {
    SparkListenerJobStart jobStart = ListenerTestHelper.listenerJobStart(1);
    SparkListenerJob listenerJob = new SparkListenerJob(jobStart, null);

    assertNull(listenerJob.getJobUIAddress());
    assertEquals(listenerJob.getJobId(), 1);
    assertEquals(listenerJob.getStageIds().length, 0);
    assertEquals(listenerJob.getJobName(), "Job 1");
    assertEquals(listenerJob.getJobDescription(), "No stages information available");
  }

  @Test
  public void addNewJobGroup() {
    SparkJobDetailsListener listener = new SparkJobDetailsListener("localhost");
    assertTrue(listener.getTable().isEmpty());
    listener.addJob("jobGroup", ListenerTestHelper.listenerJobStart(1));
    assertEquals(listener.getTable().size(), 1);
    assertEquals(listener.getTable().get("jobGroup").size(), 1);
  }

  @Test
  public void addToExistingGroup() {
    SparkJobDetailsListener listener = new SparkJobDetailsListener("localhost");
    assertTrue(listener.getTable().isEmpty());
    listener.addJob("jobGroup", ListenerTestHelper.listenerJobStart(1));
    listener.addJob("jobGroup", ListenerTestHelper.listenerJobStart(2));
    assertEquals(listener.getTable().size(), 1);
    assertEquals(listener.getTable().get("jobGroup").size(), 2);
  }

  @Test
  public void resetJobGroup() {
    SparkJobDetailsListener listener = new SparkJobDetailsListener("localhost");
    assertTrue(listener.getTable().isEmpty());
    listener.addJob("jobGroup1", ListenerTestHelper.listenerJobStart(1));
    listener.addJob("jobGroup1", ListenerTestHelper.listenerJobStart(2));
    listener.addJob("jobGroup2", ListenerTestHelper.listenerJobStart(3));

    listener.reset("jobGroup1");
    assertEquals(listener.getTable().size(), 1);
    assertEquals(listener.getTable().get("jobGroup1").size(), 0);
    assertEquals(listener.getTable().get("jobGroup2").size(), 1);
  }

  @Test
  public void resetListener() {
    SparkJobDetailsListener listener = new SparkJobDetailsListener("localhost");
    assertTrue(listener.getTable().isEmpty());
    listener.addJob("jobGroup1", ListenerTestHelper.listenerJobStart(1));
    listener.addJob("jobGroup1", ListenerTestHelper.listenerJobStart(2));
    listener.addJob("jobGroup2", ListenerTestHelper.listenerJobStart(3));

    listener.reset();
    assertEquals(listener.getTable().size(), 0);
    assertNull(listener.getTable().get("jobGroup1"));
    assertNull(listener.getTable().get("jobGroup2"));
  }

  @Test
  public void getJobsForGroup() {
    SparkJobDetailsListener listener = new SparkJobDetailsListener("localhost");
    assertTrue(listener.getTable().isEmpty());
    listener.addJob("jobGroup1", ListenerTestHelper.listenerJobStart(1));
    listener.addJob("jobGroup1", ListenerTestHelper.listenerJobStart(2));
    listener.addJob("jobGroup2", ListenerTestHelper.listenerJobStart(3));

    // get job that does not exist
    assertEquals(listener.getJobs("non-existent").length, 0);
    assertEquals(listener.getJobs("jobGroup1").length, 2);
    assertEquals(listener.getJobs("jobGroup2").length, 1);
  }

  @Test
  public void processRelevantEvents() {
    SparkJobDetailsListener listener = new SparkJobDetailsListener("localhost");
    listener.onEvent(null);
    // should also be no-op since properties == null
    listener.onEvent(ListenerTestHelper.listenerJobStart(1));

    Properties props = new Properties();
    props.setProperty("spark.jobGroup.id", "jobGroup1");
    listener.onEvent(ListenerTestHelper.listenerJobStart(2, new StageInfo[] {}, props));
    listener.onEvent(ListenerTestHelper.listenerJobStart(3, new StageInfo[] {}, props));

    props.setProperty("spark.jobGroup.id", "jobGroup2");
    listener.onEvent(ListenerTestHelper.listenerJobStart(4, new StageInfo[] {}, props));

    // check that we have correct number of job groups
    assertEquals(listener.getTable().size(), 2);
    assertEquals(listener.getJobs("jobGroup1").length, 2);
    assertEquals(listener.getJobs("jobGroup2").length, 1);
  }
}
