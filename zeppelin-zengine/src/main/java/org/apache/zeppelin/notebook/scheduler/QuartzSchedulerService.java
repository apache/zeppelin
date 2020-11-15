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
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuartzSchedulerService implements SchedulerService {
  private static final Logger LOGGER = LoggerFactory.getLogger(QuartzSchedulerService.class);

  private final ZeppelinConfiguration zeppelinConfiguration;
  private final Notebook notebook;
  private final Scheduler scheduler;
  private final Thread loadingNotesThread;

  @Inject
  public QuartzSchedulerService(ZeppelinConfiguration zeppelinConfiguration, Notebook notebook)
      throws SchedulerException {
    this.zeppelinConfiguration = zeppelinConfiguration;
    this.notebook = notebook;
    this.scheduler = getScheduler();
    this.scheduler.start();

    // Do in a separated thread because there may be many notes,
    // loop all notes in the main thread may block the restarting of Zeppelin server
    // TODO(zjffdu) It may cause issue when user delete note before this thread is finished
    this.loadingNotesThread = new Thread(() -> {
        LOGGER.info("Starting init cronjobs");
        notebook.getNotesInfo().stream()
                .forEach(entry -> {
                  try {
                    if (!refreshCron(entry.getId())) {
                      try {
                        LOGGER.debug("Unload note: {}", entry.getId());
                        notebook.getNote(entry.getId()).unLoad();
                      } catch (Exception e) {
                        LOGGER.warn("Fail to unload note: {}", entry.getId(), e);
                      }
                    }
                  } catch (Exception e) {
                    LOGGER.warn("Fail to refresh cron for note: {}", entry.getId());
                  }
                });
        LOGGER.info("Complete init cronjobs");
    });
    loadingNotesThread.setName("Init CronJob Thread");
    loadingNotesThread.setDaemon(true);
    loadingNotesThread.start();
  }

  private Scheduler getScheduler() throws SchedulerException {
    return new StdSchedulerFactory().getScheduler();
  }

  /**
   * This is only for testing, unit test should always call this method in setup() before testing.
   */
  @VisibleForTesting
  public void waitForFinishInit() {
    try {
      loadingNotesThread.join();
    } catch (InterruptedException e) {
      LOGGER.warn("Unexpected exception", e);
    }
  }

  @Override
  public boolean refreshCron(String noteId) {
    removeCron(noteId);
    Note note = null;
    try {
      note = notebook.getNote(noteId);
    } catch (IOException e) {
      LOGGER.warn("Skip refresh cron of note: {} because fail to get it", noteId, e);
      return false;
    }
    if (note == null) {
      LOGGER.warn("Skip refresh cron of note: {} because there's no such note", noteId);
      return false;
    }
    if (note.isTrash()) {
      LOGGER.warn("Skip refresh cron of note: {} because it is in trash", noteId);
      return false;
    }

    Map<String, Object> config = note.getConfig();
    if (config == null) {
      LOGGER.warn("Skip refresh cron of note: {} because its config is empty.", noteId);
      return false;
    }

    if (!note.isCronSupported(zeppelinConfiguration)) {
      LOGGER.warn("Skip refresh cron of note {} because its cron is not enabled.", noteId);
      return false;
    }

    String cronExpr = (String) note.getConfig().get("cron");
    if (cronExpr == null || cronExpr.trim().length() == 0) {
      LOGGER.warn("Skip refresh cron of note {} because its cron expression is empty.", noteId);
      return false;
    }

    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put("note", note);
    JobDetail newJob =
        JobBuilder.newJob(CronJob.class)
            .withIdentity(noteId, "note")
            .setJobData(jobDataMap)
            .build();

    Map<String, Object> info = note.getInfo();
    info.put("cron", null);

    CronTrigger trigger = null;
    try {
      trigger =
          TriggerBuilder.newTrigger()
              .withIdentity("trigger_" + noteId, "note")
              .withSchedule(CronScheduleBuilder.cronSchedule(cronExpr))
              .forJob(noteId, "note")
              .build();
    } catch (Exception e) {
      LOGGER.error("Fail to create cron trigger for note: {}", note.getName(), e);
      info.put("cron", e.getMessage());
      return false;
    }

    try {
      LOGGER.info("Trigger cron for note: {}, with cron expression: {}",  note.getName(), cronExpr);
      scheduler.scheduleJob(newJob, trigger);
      return true;
    } catch (SchedulerException e) {
      LOGGER.error("Fail to schedule cron job for note: {}", note.getName(), e);
      info.put("cron", "Scheduler Exception");
      return false;
    }
  }

  @Override
  public Set<?> getJobs() {
    try {
      return scheduler.getJobKeys(GroupMatcher.anyGroup());
    } catch (SchedulerException e) {
      LOGGER.error("Error while getting jobKeys", e);
      return Collections.emptySet();
    }
  }

  private void removeCron(String id) {
    try {
      scheduler.deleteJob(new JobKey(id, "note"));
    } catch (SchedulerException e) {
      LOGGER.error("Can't remove quertz {}", id, e);
    }
  }
}
