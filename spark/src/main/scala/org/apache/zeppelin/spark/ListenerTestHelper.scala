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

package org.apache.zeppelin.spark

import java.util.Properties

import org.apache.spark.scheduler.{SparkListenerJobStart, StageInfo}

/** Helper methods for Java tests */
private[spark] object ListenerTestHelper {
  def stageInfo(stageId: Int): StageInfo = {
    new StageInfo(stageId, 0, s"Stage $stageId", 100, Seq.empty, Seq.empty, "details")
  }

  def listenerJobStart(
      jobId: Int,
      stageInfos: Array[StageInfo],
      properties: Properties): SparkListenerJobStart = {
    new SparkListenerJobStart(jobId, System.currentTimeMillis, stageInfos.toSeq, properties)
  }

  def listenerJobStart(jobId: Int, stageInfos: Array[StageInfo]): SparkListenerJobStart = {
    listenerJobStart(jobId, stageInfos, null)
  }

  def listenerJobStart(jobId: Int): SparkListenerJobStart = {
    listenerJobStart(jobId, Array.empty)
  }
}
