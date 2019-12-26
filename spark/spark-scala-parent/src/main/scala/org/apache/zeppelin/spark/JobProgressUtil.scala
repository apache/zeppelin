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

import org.apache.spark.SparkContext
import org.slf4j.{Logger, LoggerFactory}

object JobProgressUtil {

  protected lazy val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  def progress(sc: SparkContext, jobGroup : String):Int = {
    // Each paragraph has one unique jobGroup, and one paragraph may run multiple times.
    // So only look for the first job which match the jobGroup
    val jobInfo = sc.statusTracker
      .getJobIdsForGroup(jobGroup)
      .headOption
      .flatMap(jobId => sc.statusTracker.getJobInfo(jobId))
    val stagesInfoOption = jobInfo.flatMap( jobInfo => Some(jobInfo.stageIds().flatMap(sc.statusTracker.getStageInfo)))
    stagesInfoOption match {
      case None => 0
      case Some(stagesInfo) =>
        val taskCount = stagesInfo.map(_.numTasks).sum
        val completedTaskCount = stagesInfo.map(_.numCompletedTasks).sum
        LOGGER.debug("Total TaskCount: " + taskCount)
        LOGGER.debug("Completed TaskCount: " + completedTaskCount)
        if (taskCount == 0) {
          0
        } else {
          (100 * completedTaskCount.toDouble / taskCount).toInt
        }
    }
  }
}
