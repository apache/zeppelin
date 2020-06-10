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

package org.apache.zeppelin.flink;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JobManagerTest {

  @Test
  public void testRichDuration() {
    String richDuration = JobManager.toRichTimeDuration(18);
    assertEquals("18 seconds", richDuration);

    richDuration = JobManager.toRichTimeDuration(120);
    assertEquals("2 minutes 0 seconds", richDuration);

    richDuration = JobManager.toRichTimeDuration(60 * 60 + 1);
    assertEquals("1 hours 0 minutes 1 seconds", richDuration);

    richDuration = JobManager.toRichTimeDuration(60 * 60 + 60 + 1);
    assertEquals("1 hours 1 minutes 1 seconds", richDuration);

    richDuration = JobManager.toRichTimeDuration(24 * 60 * 60 + 60 + 1);
    assertEquals("1 days 0 hours 1 minutes 1 seconds", richDuration);
  }
}
