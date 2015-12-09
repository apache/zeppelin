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

package org.apache.zeppelin.notebook.repo;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.notebook.JobListenerFactory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookTest;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.scheduler.Job.Status;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VFSNotebookRepoTest implements JobListenerFactory{
  private static final Logger logger = LoggerFactory.getLogger(NotebookTest.class);

  private ZeppelinConfiguration conf;
  private SchedulerFactory schedulerFactory;
  private Notebook notebook;
  private NotebookRepo notebookRepo;
  private InterpreterFactory factory;

  @Before
  public void setUp() throws Exception {    
    conf = ZeppelinConfiguration.create();

    this.schedulerFactory = new SchedulerFactory();
    factory = new InterpreterFactory(conf, new InterpreterOption(false), null);

    notebookRepo = new VFSNotebookRepo(conf);
    notebook = new Notebook(conf, notebookRepo, schedulerFactory, factory, this);
  }

  @After
  public void tearDown() throws Exception {
  }
  
  @Test
  public void testSaveNotebook() throws IOException, InterruptedException {
    Note note = notebook.createNote("anonymous");
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    Paragraph p1 = note.addParagraph();
    Map config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("%md hello world");
    
    note.run(p1.getId());
    int timeout = 1;
    while (!p1.isTerminated()) {
      Thread.sleep(1000);
      if (timeout++ > 10) {
        break;
      }
    }
    int i = 0;
    int TEST_COUNT = 10;
    while (i++ < TEST_COUNT) {
      p1.setText("%md hello zeppelin");
      new Thread(new NotebookWriter(note)).start();
      p1.setText("%md hello world");
      new Thread(new NotebookWriter(note)).start();
    }
  
    note.setName("SaveTest");
    notebookRepo.save(note);
    assertEquals(note.getName(), "SaveTest");
  }
  
  class NotebookWriter implements Runnable {
    Note note;
    public NotebookWriter(Note note) {
      this.note = note;
    }
    
    @Override
    public void run() {
      try {
        notebookRepo.save(note);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public JobListener getParagraphJobListener(Note note) {
    return null;
  }
}
