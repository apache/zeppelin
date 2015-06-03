package org.apache.zeppelin.rest;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.server.ZeppelinServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.Gson;

/**
 * Test against spark cluster.
 * Spark cluster is started by CI server using testing/startSparkCluster.sh
 */
public class ZeppelinSparkClusterTest extends AbstractTestRestApi {
  Gson gson = new Gson();

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp();
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  private void waitForFinish(Paragraph p) {
    while (p.getStatus() != Status.FINISHED) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  @Test
  public void getApiRoot() throws IOException {
    // create new note
    Note note = ZeppelinServer.notebook.createNote();
    note.addParagraph();
    Paragraph p = note.getLastParagraph();

    // get spark version string
    p.setText("print(sc.version)");
    note.run(p.getId());
    waitForFinish(p);
    String sparkVersion = p.getResult().message();

    // run markdown paragraph, again
    p = note.addParagraph();
    p.setText("print(sc.parallelize(1 to 10).reduce(_ + _))");
    note.run(p.getId());
    waitForFinish(p);
    assertEquals("55", p.getResult().message());
    ZeppelinServer.notebook.removeNote(note.id());
  }
}
