package org.apache.zeppelin.notebook.scheduler;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.notebook.Note;
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
    Note note = (Note) jobDataMap.get("note");
    LOGGER.info("Start cron job of note: {}", note.getId());
    cronJobTimerSamples.put(context, Timer.start(Metrics.globalRegistry));
  }

  @Override
  public void jobExecutionVetoed(JobExecutionContext context) {
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
    Note note = (Note) jobDataMap.get("note");
    LOGGER.info("vetoed cron job of note: {}", note.getId());
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
    Note note = (Note) jobDataMap.get("note");
    String result = StringUtils.defaultString(context.getResult().toString(), "unknown");
    LOGGER.info("cron job of noteId {} executed with result {}", note.getId(), result);
    Timer.Sample sample = cronJobTimerSamples.remove(context);
    if (sample != null) {
      Tag noteId = Tag.of("nodeid", note.getId());
      Tag name = Tag.of("name", StringUtils.defaultString(note.getName(), "unknown"));
      Tag statusTag = Tag.of("result", result);
      sample.stop(Metrics.timer("cronjob", Tags.of(noteId, name, statusTag)));
    } else {
      LOGGER.warn("No Timer.Sample for NoteId {} found", note.getId());
    }
  }
}
