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

package org.apache.zeppelin.notebook.scheduler;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/** Cron task for the note. */
public class CronJob implements org.quartz.Job {
  private static final Logger LOGGER = LoggerFactory.getLogger(CronJob.class);

  private static final String RESULT_SUCCEEDED = "succeeded";
  private static final String RESULT_FAILED = "failed";
  private static final String RESULT_SKIPPED = "skipped";

  @Override
  public void execute(JobExecutionContext context) {
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
    Note note = (Note) jobDataMap.get("note");
    if (note.haveRunningOrPendingParagraphs()) {
      LOGGER.warn(
          "execution of the cron job is skipped because there is a running or pending "
              + "paragraph (note id: {})",
          note.getId());
      context.setResult(RESULT_SKIPPED);
      return;
    }

    String cronExecutingUser = (String) note.getConfig().get("cronExecutingUser");
    String cronExecutingRoles = (String) note.getConfig().get("cronExecutingRoles");
    if (null == cronExecutingUser) {
      cronExecutingUser = "anonymous";
    }
    AuthenticationInfo authenticationInfo =
            new AuthenticationInfo(
                    cronExecutingUser,
                    StringUtils.isEmpty(cronExecutingRoles) ? null : cronExecutingRoles,
                    null);
    try {
      note.runAll(authenticationInfo, true, true, new HashMap<>());
      context.setResult(RESULT_SUCCEEDED);
    } catch (Exception e) {
      context.setResult(RESULT_FAILED);
      LOGGER.warn("Fail to run note: {}", note.getName(), e);
    }
  }
}
