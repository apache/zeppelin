package com.nflabs.zeppelin.notebook;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.SchedulerException;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.interpreter.mock.MockInterpreter1;
import com.nflabs.zeppelin.interpreter.mock.MockInterpreter2;
import com.nflabs.zeppelin.notebook.Note;
import com.nflabs.zeppelin.notebook.Notebook;
import com.nflabs.zeppelin.notebook.Paragraph;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

public class NotebookTest implements JobListenerFactory{

	private File tmpDir;
	private ZeppelinConfiguration conf;
	private SchedulerFactory schedulerFactory;
	private File notebookDir;
	private Notebook notebook;
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
		System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "com.nflabs.zeppelin.interpreter.mock.MockInterpreter1,com.nflabs.zeppelin.interpreter.mock.MockInterpreter2");
		
		conf = ZeppelinConfiguration.create();
        
		this.schedulerFactory = new SchedulerFactory();
		
    MockInterpreter1.register("mock1", "com.nflabs.zeppelin.interpreter.mock.MockInterpreter1");
    MockInterpreter2.register("mock2", "com.nflabs.zeppelin.interpreter.mock.MockInterpreter2");
    
    factory = new InterpreterFactory(conf);
		
		notebook = new Notebook(conf, schedulerFactory, factory, this);
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
		
		Notebook notebook2 = new Notebook(conf, schedulerFactory, new InterpreterFactory(conf), this);
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
