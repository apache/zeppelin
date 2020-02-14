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

  @Inject
  public QuartzSchedulerService(ZeppelinConfiguration zeppelinConfiguration, Notebook notebook)
      throws SchedulerException {
    this.zeppelinConfiguration = zeppelinConfiguration;
    this.notebook = notebook;
    this.scheduler = new StdSchedulerFactory().getScheduler();
    this.scheduler.start();

    // Do in a separated thread because there may be many notes,
    // loop all notes in the main thread may block the restarting of Zeppelin server
    Thread loadingNotesThread = new Thread(() -> {
        LOGGER.info("Starting init cronjobs");
        notebook.getNotesInfo().stream()
                .forEach(entry -> refreshCron(entry.getId()));
        LOGGER.info("Complete init cronjobs");
    });
    loadingNotesThread.setName("Init CronJob Thread");
    loadingNotesThread.setDaemon(true);
    loadingNotesThread.start();
  }

  @Override
  public void refreshCron(String noteId) {
    removeCron(noteId);
    Note note = null;
    try {
      note = notebook.getNote(noteId);
    } catch (IOException e) {
      LOGGER.warn("Skip refresh cron of note: " + noteId + " because fail to get it", e);
      return;
    }
    if (note == null) {
      LOGGER.warn("Skip refresh cron of note: " + noteId + " because there's no such note");
      return;
    }
    if (note.isTrash()) {
      LOGGER.warn("Skip refresh cron of note: " + noteId + " because it is in trash");
      return;
    }

    Map<String, Object> config = note.getConfig();
    if (config == null) {
      LOGGER.warn("Skip refresh cron of note: " + noteId + " because its config is empty.");
      return;
    }

    if (!note.isCronSupported(zeppelinConfiguration)) {
      LOGGER.warn("Skip refresh cron of note " + noteId + " because its cron is not enabled.");
      return;
    }

    String cronExpr = (String) note.getConfig().get("cron");
    if (cronExpr == null || cronExpr.trim().length() == 0) {
      LOGGER.warn("Skip refresh cron of note " + noteId + " because its cron expression is empty.");
      return;
    }

    JobDataMap jobDataMap =
        new JobDataMap() {
          {
            put("noteId", noteId);
            put("notebook", notebook);
          }
        };
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
      LOGGER.error("Fail to create cron trigger for note: " + note.getName(), e);
      info.put("cron", e.getMessage());
    }

    try {
      if (trigger != null) {
        LOGGER.info("Trigger cron for note: " + note.getName() + ", with cron expression: " + cronExpr);
        scheduler.scheduleJob(newJob, trigger);
      }
    } catch (SchedulerException e) {
      LOGGER.error("Fail to schedule cron job for note: " + note.getName(), e);
      info.put("cron", "Scheduler Exception");
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
      LOGGER.error("Can't remove quertz " + id, e);
    }
  }
}
