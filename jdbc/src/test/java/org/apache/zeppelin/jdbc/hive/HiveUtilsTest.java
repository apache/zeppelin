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

package org.apache.zeppelin.jdbc.hive;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HiveUtilsTest {

  @Test
  public void testJobURL() {
    Optional<String> jobURL = HiveUtils.extractMRJobURL(
            "INFO  : The url to track the job: " +
            "http://localhost:8088/proxy/application_1591195707498_0064/\n" +
            "INFO  : Starting Job = job_1591195707498_0064, " +
            "Tracking URL = http://localhost:8088/proxy/application_1591195707498_0064/\n" +
            "INFO  : Kill Command = /Users/abc/Java/lib/hadoop-2.7.7/bin/hadoop job " +
            " -kill job_1591195707498_0064");
    assertTrue(jobURL.isPresent());
    assertEquals("http://localhost:8088/proxy/application_1591195707498_0064/", jobURL.get());
  }

  @Test
  public void testTezAppId() {
    Optional<String> appId = HiveUtils.extractTezAppId(
            "Query ID = hadoop_20210514105011_6f620c39-f557-4fc6-a899-ecd892be2652\n" +
                    "Total jobs = 1\n" +
                    "Launching Job 1 out of 1\n" +
                    "Status: Running " +
                    "(Executing on YARN cluster with App id application_1612885840821_260263)");
    assertTrue(appId.isPresent());
    assertEquals("application_1612885840821_260263", appId.get());
  }
}
