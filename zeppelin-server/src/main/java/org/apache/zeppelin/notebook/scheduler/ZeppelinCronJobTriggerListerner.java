package org.apache.zeppelin.notebook.scheduler;

import java.io.IOException;

import org.apache.zeppelin.notebook.Notebook;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.TriggerListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeppelinCronJobTriggerListerner implements TriggerListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinCronJobTriggerListerner.class);

  @Override
  public String getName() {
    return "ZeppelinCronJobTriggerListerner";
  }

  @Override
  public void triggerFired(Trigger trigger, JobExecutionContext context) {
    // Do nothing
  }

  @Override
  public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
    String noteId = jobDataMap.getString("noteId");
    Notebook notebook = (Notebook) jobDataMap.get("notebook");
    try {
        return notebook.processNote(noteId, note -> {
            if (note.haveRunningOrPendingParagraphs()) {
                LOGGER.warn(
                    "execution of the cron job is skipped because there is a running or pending paragraph (noteId: {})",
                    noteId);
                return true;
            }
            return false;
        });
    } catch (IOException e) {
        LOGGER.warn("Failed to check CronJob of note: {} because fail to get it", noteId ,e);
        return true;
    }
  }

  @Override
  public void triggerMisfired(Trigger trigger) {
    // Do nothing
  }

  @Override
  public void triggerComplete(Trigger trigger, JobExecutionContext context,
      CompletedExecutionInstruction triggerInstructionCode) {
    // Do nothing
  }

}
