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

  public SequentialNoteRunner(Note note) {
    this.note = note;
  }

  @Override
  public void run() {
    logger.info("Sequential run for note {} started", note.getId());
    for (Paragraph paragraph: note.getParagraphs()) {
      String paragraphId = paragraph.getId();
      logger.info("Running paragraph {}", paragraphId);
      note.run(paragraphId);
      logger.info("after run");
      Object synchronizer = note.getSequentialNoteRunInfo().getSynchronizer();
      synchronized (synchronizer) {
        Status paragraphStatus = paragraph.getStatus();
        logger.info("status = {}", paragraphStatus);
        while (paragraphStatus == null || (paragraphStatus != Status.FINISHED
            && paragraphStatus != Status.ERROR)) {
          try {
            logger.info("waiting on {}", synchronizer);
            synchronizer.wait();
            paragraphStatus = paragraph.getStatus();
            logger.info("status: {}", paragraphStatus);
          } catch (InterruptedException e) {
            logger.error("Exception while waiting for status of paragraph {} to change",
                paragraphId, e);
          }
        }
        logger.info("Paragraph {} execution complete", paragraphId);
      }
    }
    note.getSequentialNoteRunInfo().setRunningSequentially(false);
    logger.info("Sequential run for note {} finished", note.getId());
  }

}
