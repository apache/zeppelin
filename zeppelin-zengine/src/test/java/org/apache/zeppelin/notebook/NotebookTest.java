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

package org.apache.zeppelin.notebook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.SchedulerException;

public class NotebookTest implements JobListenerFactory{

	private File tmpDir;
	private ZeppelinConfiguration conf;
	private SchedulerFactory schedulerFactory;
	private File notebookDir;
	private Notebook notebook;
	private NotebookRepo notebookRepo;
  private InterpreterFactory factory;

	@Before
	public void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
		tmpDir.mkdirs();
		new File(tmpDir, "conf").mkdirs();
		notebookDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis()+"/notebook");
		notebookDir.mkdirs();

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), tmpDir.getAbsolutePath());
		System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());
		System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "org.apache.zeppelin.interpreter.mock.MockInterpreter1,org.apache.zeppelin.interpreter.mock.MockInterpreter2");

		conf = ZeppelinConfiguration.create();

		this.schedulerFactory = new SchedulerFactory();

    MockInterpreter1.register("mock1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1");
    MockInterpreter2.register("mock2", "org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    factory = new InterpreterFactory(conf, new InterpreterOption(false), null);

    notebookRepo = new VFSNotebookRepo(conf);
		notebook = new Notebook(conf, notebookRepo, schedulerFactory, factory, this);
	}

	@After
	public void tearDown() throws Exception {
		delete(tmpDir);
	}

	@Test
	public void testSelectingReplImplementation() throws IOException {
		Note note = notebook.createNote();
		note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

		// run with defatul repl
		Paragraph p1 = note.addParagraph();
		p1.setText("hello world");
		note.run(p1.getId());
		while(p1.isTerminated()==false || p1.getResult()==null) Thread.yield();
		assertEquals("repl1: hello world", p1.getResult().message());

		// run with specific repl
		Paragraph p2 = note.addParagraph();
		p2.setText("%mock2 hello world");
		note.run(p2.getId());
		while(p2.isTerminated()==false || p2.getResult()==null) Thread.yield();
		assertEquals("repl2: hello world", p2.getResult().message());
	}

	@Test
	public void testPersist() throws IOException, SchedulerException{
		Note note = notebook.createNote();

		// run with default repl
		Paragraph p1 = note.addParagraph();
		p1.setText("hello world");
		note.persist();

		Notebook notebook2 = new Notebook(conf, notebookRepo, schedulerFactory, new InterpreterFactory(conf, null), this);
		assertEquals(1, notebook2.getAllNotes().size());
	}

	@Test
	public void testRunAll() throws IOException {
		Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

		Paragraph p1 = note.addParagraph();
		p1.setText("p1");
		Paragraph p2 = note.addParagraph();
		p2.setText("p2");
		assertEquals(null, p2.getResult());
		note.runAll();

		while(p2.isTerminated()==false || p2.getResult()==null) Thread.yield();
		assertEquals("repl1: p2", p2.getResult().message());
	}

	@Test
	public void testSchedule() throws InterruptedException, IOException{
		// create a note and a paragraph
		Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

		Paragraph p = note.addParagraph();
		p.setText("p1");
		Date dateFinished = p.getDateFinished();
		assertNull(dateFinished);

		// set cron scheduler, once a second
		Map<String, Object> config = note.getConfig();
		config.put("cron", "* * * * * ?");
		note.setConfig(config);
		notebook.refreshCron(note.id());
		Thread.sleep(1*1000);
		dateFinished = p.getDateFinished();
		assertNotNull(dateFinished);

		// remove cron scheduler.
		config.put("cron", null);
		note.setConfig(config);
		notebook.refreshCron(note.id());
		Thread.sleep(1*1000);
		assertEquals(dateFinished, p.getDateFinished());
	}

  @Test
  public void testAngularObjectRemovalOnNotebookRemove() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    AngularObjectRegistry registry = note.getNoteReplLoader()
        .getInterpreterSettings().get(0).getInterpreterGroup()
        .getAngularObjectRegistry();

    // add local scope object
    registry.add("o1", "object1", note.id());
    // add global scope object
    registry.add("o2", "object2", null);

    // remove notebook
    notebook.removeNote(note.id());

    // local object should be removed
    assertNull(registry.get("o1", note.id()));
    // global object sould be remained
    assertNotNull(registry.get("o2", null));
	}

  @Test
  public void testAngularObjectRemovalOnInterpreterRestart() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    AngularObjectRegistry registry = note.getNoteReplLoader()
        .getInterpreterSettings().get(0).getInterpreterGroup()
        .getAngularObjectRegistry();

    // add local scope object
    registry.add("o1", "object1", note.id());
    // add global scope object
    registry.add("o2", "object2", null);

    // restart interpreter
    factory.restart(note.getNoteReplLoader().getInterpreterSettings().get(0).id());
    registry = note.getNoteReplLoader()
    .getInterpreterSettings().get(0).getInterpreterGroup()
    .getAngularObjectRegistry();

    // local and global scope object should be removed
    assertNull(registry.get("o1", note.id()));
    assertNull(registry.get("o2", null));
    notebook.removeNote(note.id());
  }

	private void delete(File file){
		if(file.isFile()) file.delete();
		else if(file.isDirectory()){
			File [] files = file.listFiles();
			if(files!=null && files.length>0){
				for(File f : files){
					delete(f);
				}
			}
			file.delete();
		}
	}

	@Override
	public JobListener getParagraphJobListener(Note note) {
		return new JobListener(){

			@Override
			public void onProgressUpdate(Job job, int progress) {
			}

			@Override
			public void beforeStatusChange(Job job, Status before, Status after) {
			}

			@Override
			public void afterStatusChange(Job job, Status before, Status after) {
			}
		};
	}
}
