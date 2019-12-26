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
  private static final Logger logger = LoggerFactory.getLogger(CronJob.class);

  @Override
  public void execute(JobExecutionContext context) {
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();

    Notebook notebook = (Notebook) jobDataMap.get("notebook");
    String noteId = jobDataMap.getString("noteId");
    Note note = null;
    try {
      note = notebook.getNote(noteId);
      if (note == null) {
        logger.warn("Note " + noteId + " not found");
        return;
      }
    } catch (IOException e) {
      logger.warn("Fail to get note: " + noteId, e);
      return;
    }
    if (note.haveRunningOrPendingParagraphs()) {
      logger.warn(
          "execution of the cron job is skipped because there is a running or pending "
              + "paragraph (note id: {})",
          noteId);
      return;
    }

    if (!note.isCronSupported(notebook.getConf())) {
      logger.warn("execution of the cron job is skipped cron is not enabled from Zeppelin server");
      return;
    }

    runAll(note);

    boolean releaseResource = false;
    String cronExecutingUser = null;
    try {
      Map<String, Object> config = note.getConfig();
      if (config != null) {
        if (config.containsKey("releaseresource")) {
          releaseResource = (boolean) config.get("releaseresource");
        }
        cronExecutingUser = (String) config.get("cronExecutingUser");
      }
    } catch (ClassCastException e) {
      logger.error(e.getMessage(), e);
    }
    if (releaseResource) {
      for (InterpreterSetting setting : note.getBindedInterpreterSettings()) {
        try {
          notebook
              .getInterpreterSettingManager()
              .restart(
                  setting.getId(),
                  noteId,
                  cronExecutingUser != null ? cronExecutingUser : "anonymous");
        } catch (InterpreterException e) {
          logger.error("Fail to restart interpreter: " + setting.getId(), e);
        }
      }
    }
  }

  void runAll(Note note) {
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
    note.runAll(authenticationInfo, true);
  }
}
