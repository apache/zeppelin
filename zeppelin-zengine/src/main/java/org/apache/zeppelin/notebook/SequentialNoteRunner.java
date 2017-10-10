package org.apache.zeppelin.notebook;

import org.apache.zeppelin.scheduler.Job.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs all paragraphs of a notebook in a sequential manner.
 */
public class SequentialNoteRunner implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(SequentialNoteRunner.class);

  private Note note;
  private SequentialNoteRunListener listener;

  public SequentialNoteRunner(Note note, SequentialNoteRunListener listener) {
    this.note = note;
    this.listener = listener;
  }

  @Override
  public void run() {
    logger.info("Sequential run for note {} started", note.getId());
    for (Paragraph paragraph: note.getParagraphs()) {
      String paragraphId = paragraph.getId();
      logger.info("Running paragraph {}", paragraphId);
      try {
        note.run(paragraphId);
        Object synchronizer = note.getSequentialNoteRunInfo().getSynchronizer();
        synchronized (synchronizer) {
          Status paragraphStatus = paragraph.getStatus();
          while (paragraphStatus == null || paragraphStatus == Status.PENDING
                 || paragraphStatus == Status.RUNNING) {
            try {
              synchronizer.wait();
              paragraphStatus = paragraph.getStatus();
            } catch (InterruptedException e) {
              logger.error("Exception while waiting for status of paragraph {} to change",
                  paragraphId, e);
            }
          }
          logger.info("Paragraph {} execution complete", paragraphId);
        }
      } catch (Exception e) {
        logger.error("Error while running paragraph {}", paragraphId, e);
      }
    }
    note.getSequentialNoteRunInfo().setRunningSequentially(false);
    listener.onSequentialRunFinished(note);
    logger.info("Sequential run for note {} finished", note.getId());
  }

}
