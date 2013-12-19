package com.nflabs.zeppelin.server;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import org.quartz.SchedulerException;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;
import com.nflabs.zeppelin.zengine.Z;

public class ZQLSessionManagerTest extends TestCase {

	private File tmpDir;
	private SchedulerFactory schedulerFactory;
	private ZQLJobManager sm;
	private File dataDir;


	protected void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());		
		tmpDir.mkdir();
		dataDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis()+"/data");
		dataDir.mkdir();
		System.setProperty("hive.local.warehouse", "file://"+dataDir.getAbsolutePath());
		System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpDir.toURI().toString());
		System.setProperty(ConfVars.ZEPPELIN_JOB_DIR.getVarName(), tmpDir.getAbsolutePath());
		Z.configure();

		this.schedulerFactory = new SchedulerFactory();

		this.sm = new ZQLJobManager(schedulerFactory.createOrGetFIFOScheduler("analyze"), Z.fs(), Z.getConf().getString(ConfVars.ZEPPELIN_JOB_DIR));
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
		ZQLJob sess = sm.create();
		assertNotNull(sess);
		
		// List
		assertEquals(1, sm.list().size());
		
		// Update
		sm.setZql(sess.getId(), "show tables");
		
		// Get
		assertEquals("show tables", sm.get(sess.getId()).getZQL());
		
		// Delete
		sm.delete(sess.getId());
		assertNull(sm.get(sess.getId()));
		
		// List
		assertEquals(0, sm.list().size());
	}
	
	public void testRun() throws InterruptedException, SchedulerException{
		// Create
		ZQLJob sess = sm.create();
		sm.setZql(sess.getId(), "show tables");
		
		// check if new session manager read
		sm = new ZQLJobManager(schedulerFactory.createOrGetFIFOScheduler("analyze"), Z.fs(), Z.getConf().getString(ConfVars.ZEPPELIN_JOB_DIR));
		
		// run the session
		sm.run(sess.getId());
		
		while(sm.get(sess.getId()).getStatus()!=Status.FINISHED){
			Thread.sleep(300);
		}
		
		assertEquals(Status.FINISHED, sm.get(sess.getId()).getStatus());
		
		// check if history is made
		assertEquals(sess.getId(), sm.getHistory(sess.getId(), sm.listHistory(sess.getId()).firstKey()).getId());
		
		// run session again
		sm.run(sess.getId());
		Thread.sleep(500); // wait for start;
		while(sm.get(sess.getId()).getStatus()!=Status.FINISHED){ // wait for finish
			Thread.sleep(300);
		}

		// another history made
		assertEquals(2, sm.listHistory(sess.getId()).size());
		
		// remove a history
		sm.deleteHistory(sess.getId(), sm.listHistory(sess.getId()).firstKey());
		assertEquals(1, sm.listHistory(sess.getId()).size());
		
		// remove whole history
		sm.deleteHistory(sess.getId());
		assertEquals(0, sm.listHistory(sess.getId()).size());
		
	}
	
	@SuppressWarnings("unchecked")
    public void testSerializePlan() throws InterruptedException{
		// Create
		ZQLJob sess = sm.create();
		sm.setZql(sess.getId(), "!echo hello;!echo world");

		// run the session
		sm.run(sess.getId());
		

		while(sm.get(sess.getId()).getStatus()!=Status.FINISHED){
			Thread.sleep(300);
		}
		
		assertEquals(2, ((LinkedList<Result>)sess.getReturn()).size());
		List<Result> ret = (List<Result>) sm.get(sess.getId()).getReturn();
		assertEquals(2, ret.size());
		
	}
	
	@SuppressWarnings("unchecked")
	public void testCron() throws InterruptedException{
		ZQLJob sess = sm.create();
		sm.setZql(sess.getId(), "!echo 'hello world'");
		sm.setCron(sess.getId(), "0/1 * * * * ?");

		while (sm.get(sess.getId()).getStatus()!=Status.FINISHED){
			Thread.sleep(300);
		}
		
		List<Result> ret = (List<Result>) sm.get(sess.getId()).getReturn();
		assertEquals("hello world", ret.get(0).getRows().get(0)[0]);

		Date firstDateFinished = sm.get(sess.getId()).getDateFinished();
		
		// wait for second run
		while (sm.get(sess.getId()).getDateFinished().getTime()==firstDateFinished.getTime()){
			Thread.sleep(300);
		}
		
		ret = (List<Result>) sm.get(sess.getId()).getReturn();
		assertEquals("hello world", ret.get(0).getRows().get(0)[0]);		
		
		sm.delete(sess.getId());
	}

}
