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

import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cron task for the note. */
public class CronJob implements org.quartz.Job {
  private static final Logger LOGGER = LoggerFactory.getLogger(CronJob.class);

  @Override
  public void execute(JobExecutionContext context) {
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
    Note note = (Note) jobDataMap.get("note");
    LOGGER.info("Start cron job of note: " + note.getId());
    if (note.haveRunningOrPendingParagraphs()) {
      LOGGER.warn(
          "execution of the cron job is skipped because there is a running or pending "
              + "paragraph (note id: {})",
          note.getId());
      return;
    }


    try {
      note.setCronMode(true);

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
        note.runAll(authenticationInfo, true);
      } catch (Exception e) {
        LOGGER.warn("Fail to run note: " + note.getName(), e);
      }

      LOGGER.info("Releasing interpreters used by this note: " + note.getId());
      for (InterpreterSetting setting : note.getUsedInterpreterSettings()) {
          setting.closeInterpreters(new ExecutionContext(cronExecutingUser, note.getId(), true));
      }
    } finally {
      note.setCronMode(false);
    }
  }
}
