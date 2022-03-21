package org.apache.zeppelin.notebook.scheduler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.notebook.Notebook;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

public class CronJobListener implements JobListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(CronJobListener.class);

  // JobExecutionContext -> Timer.Sample
  private Map<JobExecutionContext, Timer.Sample> cronJobTimerSamples = new HashMap<>();

  @Override
  public String getName() {
    return getClass().getSimpleName();
  }

  @Override
  public void jobToBeExecuted(JobExecutionContext context) {
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
    String noteId = jobDataMap.getString("noteId");
    LOGGER.info("Start cron job of note: {}", noteId);
    cronJobTimerSamples.put(context, Timer.start(Metrics.globalRegistry));
  }

  @Override
  public void jobExecutionVetoed(JobExecutionContext context) {
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
    String noteId = jobDataMap.getString("noteId");
    LOGGER.info("vetoed cron job of note: {}", noteId);
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
    String noteId = jobDataMap.getString("noteId");
    Notebook notebook = (Notebook) jobDataMap.get("notebook");
    String noteName = "unknown";
    try {
      noteName = notebook.processNote(noteId,
        note -> {
          if (note == null) {
            LOGGER.warn("Failed to get note: {}", noteId);
            return "unknown";
          }
          return note.getName();
        });
    } catch (IOException e) {
      LOGGER.error("Failed to get note: {}", noteId, e);
    } finally {
      Timer.Sample sample = cronJobTimerSamples.remove(context);
      String result = StringUtils.defaultString(context.getResult().toString(), "unknown");
      LOGGER.info("cron job of noteId {} executed with result {}", noteId, result);
      if (sample != null) {
        Tag noteIdTag = Tag.of("nodeid", noteId);
        Tag nameTag = Tag.of("name", noteName);
        Tag statusTag = Tag.of("result", result);
        sample.stop(Metrics.timer("cronjob", Tags.of(noteIdTag, nameTag, statusTag)));
      } else {
        LOGGER.warn("No Timer.Sample for NoteId {} found", noteId);
      }
    }
  }
}
