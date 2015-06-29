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
  public void basicRDDTransformationAndActionTest() throws IOException {
    // create new note
    Note note = ZeppelinServer.notebook.createNote();

    // run markdown paragraph, again
    Paragraph p = note.addParagraph();
    p.setText("print(sc.parallelize(1 to 10).reduce(_ + _))");
    note.run(p.getId());
    waitForFinish(p);
    assertEquals("55", p.getResult().message());
    ZeppelinServer.notebook.removeNote(note.id());
  }

  @Test
  public void pySparkTest() throws IOException {
    // create new note
    Note note = ZeppelinServer.notebook.createNote();

    int sparkVersion = getSparkVersionNumber(note);

    if (isPyspark() && sparkVersion >= 12) {   // pyspark supported from 1.2.1
      // run markdown paragraph, again
      Paragraph p = note.addParagraph();
      p.setText("%pyspark print(sc.parallelize(range(1, 11)).reduce(lambda a, b: a + b))");
      note.run(p.getId());
      waitForFinish(p);
      assertEquals("55\n", p.getResult().message());
    }
    ZeppelinServer.notebook.removeNote(note.id());
  }
  
  @Test
  public void pySparkAutoConvertOptionTest() throws IOException {
    // create new note
    Note note = ZeppelinServer.notebook.createNote();

    int sparkVersion = getSparkVersionNumber(note);

    if (isPyspark() && sparkVersion >= 14) {   // auto_convert enabled from spark 1.4
      // run markdown paragraph, again
      Paragraph p = note.addParagraph();
      p.setText("%pyspark\nfrom pyspark.sql.functions import *\n"
          + "print(sqlContext.range(0, 10).withColumn('uniform', rand(seed=10) * 3.14).count())");
      note.run(p.getId());
      waitForFinish(p);
      assertEquals("10\n", p.getResult().message());
    }
    ZeppelinServer.notebook.removeNote(note.id());
  }

  @Test
  public void zRunTest() throws IOException {
    // create new note
    Note note = ZeppelinServer.notebook.createNote();
    Paragraph p0 = note.addParagraph();
    p0.setText("z.run(1)");
    Paragraph p1 = note.addParagraph();
    p1.setText("val a=10");
    Paragraph p2 = note.addParagraph();
    p2.setText("print(a)");

    note.run(p0.getId());
    waitForFinish(p0);

    note.run(p2.getId());
    waitForFinish(p2);
    assertEquals("10", p2.getResult().message());

    ZeppelinServer.notebook.removeNote(note.id());
  }

  /**
   * Get spark version number as a numerical value.
   * eg. 1.1.x => 11, 1.2.x => 12, 1.3.x => 13 ...
   */
  private int getSparkVersionNumber(Note note) {
    Paragraph p = note.addParagraph();
    p.setText("print(sc.version)");
    note.run(p.getId());
    waitForFinish(p);
    String sparkVersion = p.getResult().message();
    System.out.println("Spark version detected " + sparkVersion);
    String[] split = sparkVersion.split("\\.");
    int version = Integer.parseInt(split[0]) * 10 + Integer.parseInt(split[1]);
    return version;
  }
}
