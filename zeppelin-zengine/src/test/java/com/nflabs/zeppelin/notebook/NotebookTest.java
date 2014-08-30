package com.nflabs.zeppelin.notebook;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.interpreter.mock.MockInterpreterFactory;
import com.nflabs.zeppelin.notebook.Note;
import com.nflabs.zeppelin.notebook.Notebook;
import com.nflabs.zeppelin.notebook.Paragraph;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

public class NotebookTest {

	private File tmpDir;
	private ZeppelinConfiguration conf;
	private SchedulerFactory schedulerFactory;
	private File notebookDir;
	private Notebook notebook;

	@Before
	public void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());		
		tmpDir.mkdirs();
		notebookDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis()+"/notebook");
		notebookDir.mkdirs();

		System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());

		conf = ZeppelinConfiguration.create();
        
		this.schedulerFactory = new SchedulerFactory();
		
		notebook = new Notebook(conf, schedulerFactory, new MockInterpreterFactory(conf));
	}

	@After
	public void tearDown() throws Exception {
		delete(tmpDir);
	}

	@Test
	public void testSelectingReplImplementation() {
		Note note = notebook.createNote();
		
		// run with defatul repl
		Paragraph p1 = note.addParagraph();
		p1.setParagraph("hello world");
		note.run(p1.getId());
		while(p1.isTerminated()==false) Thread.yield();
		assertEquals("repl1: hello world", p1.getResult().message());
		
		// run with specific repl
		Paragraph p2 = note.addParagraph();
		p2.setParagraph("%MockRepl2 hello world");
		note.run(p2.getId());
		while(p2.isTerminated()==false) Thread.yield();
		assertEquals("repl2: hello world", p2.getResult().message());
	}
	
	@Test
	public void testPersist() throws IOException{
		Note note = notebook.createNote();
		
		// run with defatul repl
		Paragraph p1 = note.addParagraph();
		p1.setParagraph("hello world");
		note.persist();
		
		Notebook notebook2 = new Notebook(conf, schedulerFactory, new MockInterpreterFactory(conf));
		assertEquals(1, notebook2.getAllNotes().size());
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
}
