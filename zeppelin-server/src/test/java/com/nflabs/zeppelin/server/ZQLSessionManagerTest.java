package com.nflabs.zeppelin.server;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;
import com.nflabs.zeppelin.zengine.Z;

import junit.framework.TestCase;

public class ZQLSessionManagerTest extends TestCase {

	private File tmpDir;
	private SchedulerFactory schedulerFactory;
	private ZQLSessionManager sm;
	private File dataDir;


	protected void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());		
		tmpDir.mkdir();
		dataDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis()+"/data");
		dataDir.mkdir();
		System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_WAREHOUSE.getVarName(), "file://"+dataDir.getAbsolutePath());
		System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpDir.toURI().toString());
		System.setProperty(ConfVars.ZEPPELIN_SESSION_DIR.getVarName(), tmpDir.getAbsolutePath());
		Z.configure();

		this.schedulerFactory = new SchedulerFactory();

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
		
		while(sm.get(sess.getId()).getStatus()!=Status.FINISHED){
			Thread.sleep(300);
		}
		
		assertEquals(Status.FINISHED, sm.get(sess.getId()).getStatus());
	}
	
	public void testSerializePlan() throws InterruptedException{
		// Create
		ZQLSession sess = sm.create();
		sm.setZql(sess.getId(), "create table if not exists test(txt STRING); show tables");

		// run the session
		sm.run(sess.getId());
		

		while(sm.get(sess.getId()).getStatus()!=Status.FINISHED){
			Thread.sleep(300);
		}
		
		assertEquals(2, ((LinkedList<Result>)sess.getReturn()).size());
		List<Result> ret = (List<Result>) sm.get(sess.getId()).getReturn();
		assertEquals(2, ret.size());
		
	}

}
