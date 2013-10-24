package com.nflabs.zeppelin.server;

import java.io.File;
import java.util.Date;
import java.util.Map;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;
import com.nflabs.zeppelin.zengine.Z;

import junit.framework.TestCase;

public class ZQLSessionManagerTest extends TestCase {

	private File tmpDir;
	private SchedulerFactory schedulerFactory;
	private ZQLSessionManager sm;


	protected void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
		tmpDir.mkdir();
		
		System.setProperty(ConfVars.ZEPPELIN_SESSION_DIR.getVarName(), tmpDir.getAbsolutePath());
		
		this.schedulerFactory = new SchedulerFactory();
		Z.configure();
		this.sm = new ZQLSessionManager(schedulerFactory.createOrGetParallelScheduler("analyze", 30), Z.fs(), Z.conf().getString(ConfVars.ZEPPELIN_SESSION_DIR));

	}

	protected void tearDown() throws Exception {
		super.tearDown();
		delete(tmpDir);
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

	public void testCRUD() {
		// Create
		ZQLSession sess = sm.create();
		assertNotNull(sess);
		
		// List
		assertEquals(1, sm.find(new Date(0), new Date(), 5).size());
		
		// Update
		sm.setZql(sess.getId(), "show tables");
		
		// Get
		assertEquals("show tables", sm.get(sess.getId()).getZQL());
		
		// Delete
		sm.delete(sess.getId());
		assertNull(sm.get(sess.getId()));
		
		// List
		assertEquals(0, sm.find(new Date(0), new Date(), 5).size());
	}
	
	public void testFindWithMax(){
		sm.create();
		sm.create();
		sm.create();
		assertEquals(3, sm.find(new Date(0), new Date(), 10).size());
		assertEquals(2, sm.find(new Date(0), new Date(), 2).size());
	}
	
	public void testRun() throws InterruptedException{
		// Create
		ZQLSession sess = sm.create();
		sm.setZql(sess.getId(), "show tables");
		
		// check if new session manager read
		sm = new ZQLSessionManager(schedulerFactory.createOrGetParallelScheduler("analyze", 30), Z.fs(), Z.conf().getString(ConfVars.ZEPPELIN_SESSION_DIR));
		
		// run the session
		sm.run(sess.getId());
		
		while(sm.get(sess.getId()).getStatus()==Status.FINISHED){
			Thread.sleep(300);
		}

	}

}
