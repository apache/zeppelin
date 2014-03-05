package com.nflabs.zeppelin.server;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.hadoop.fs.FileSystem;
import org.quartz.SchedulerException;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.mock.MockDriver;
import com.nflabs.zeppelin.driver.mock.MockDriverFactory;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.result.ResultDataException;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;
import com.nflabs.zeppelin.zengine.Zengine;

public class ZQLJobManagerTest extends TestCase {

	private File tmpDir;
	private SchedulerFactory schedulerFactory;
	private ZQLJobManager jm;
	private File dataDir;
    private Zengine z;
	private FileSystem fs;


	protected void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());		
		tmpDir.mkdir();
		dataDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis()+"/data");
		dataDir.mkdir();
		System.setProperty("hive.local.warehouse", "file://"+dataDir.getAbsolutePath());
		System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpDir.toURI().toString());
		System.setProperty(ConfVars.ZEPPELIN_JOB_DIR.getVarName(), tmpDir.getAbsolutePath());

		ZeppelinConfiguration conf = ZeppelinConfiguration.create();
		MockDriverFactory driverFactory = new MockDriverFactory();
        z = new Zengine(conf, driverFactory);
        
		this.schedulerFactory = new SchedulerFactory();
		fs = FileSystem.get(new org.apache.hadoop.conf.Configuration());

		this.jm = new ZQLJobManager(z, fs, schedulerFactory.createOrGetFIFOScheduler("analyze"), z.getConf().getString(ConfVars.ZEPPELIN_JOB_DIR));
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
		ZQLJob sess = jm.create();
		assertNotNull(sess);
		
		// List
		assertEquals(1, jm.list().size());
		
		// Update
		jm.setZql(sess.getId(), "show tables");
		
		// Get
		assertEquals("show tables", jm.get(sess.getId()).getZQL());
		
		// Delete
		jm.delete(sess.getId());
		assertNull(jm.get(sess.getId()));
		
		// List
		assertEquals(0, jm.list().size());
	}
	
	public void testRun() throws InterruptedException, SchedulerException{
		// Create
		ZQLJob sess = jm.create();
		jm.setZql(sess.getId(), "show tables");
		
		// check if new session manager read
		jm = new ZQLJobManager(z, fs, schedulerFactory.createOrGetFIFOScheduler("analyze"), z.getConf().getString(ConfVars.ZEPPELIN_JOB_DIR));
		
		// run the session
		jm.run(sess.getId());
		
		while(jm.get(sess.getId()).getStatus()!=Status.FINISHED){
			Thread.sleep(300);
		}
		
		assertEquals(Status.FINISHED, jm.get(sess.getId()).getStatus());
		
		// check if history is made
		assertEquals(sess.getId(), jm.getHistory(sess.getId(), jm.listHistory(sess.getId()).firstKey()).getId());
		
		// run session again
		jm.run(sess.getId());

		while(jm.get(sess.getId()).getStatus()!=Status.FINISHED){ // wait for finish
			Thread.sleep(300);
		}

		// another history made
		assertEquals(2, jm.listHistory(sess.getId()).size());
		
		// remove a history
		jm.deleteHistory(sess.getId(), jm.listHistory(sess.getId()).firstKey());
		assertEquals(1, jm.listHistory(sess.getId()).size());
		
		// remove whole history
		jm.deleteHistory(sess.getId());
		assertEquals(0, jm.listHistory(sess.getId()).size());
		
	}
	
	@SuppressWarnings("unchecked")
    public void testSerializePlan() throws InterruptedException{
		// Create
		ZQLJob sess = jm.create();
		jm.setZql(sess.getId(), "!echo hello;!echo world");

		// run the session
		jm.run(sess.getId());
		

		while(jm.get(sess.getId()).getStatus()!=Status.FINISHED){
			Thread.sleep(300);
		}
		
		assertEquals(2, ((LinkedList<Result>)sess.getReturn()).size());
		List<Result> ret = (List<Result>) jm.get(sess.getId()).getReturn();
		assertEquals(2, ret.size());
		
	}
	
	@SuppressWarnings("unchecked")
	public void testCron() throws InterruptedException, ResultDataException{
		MockDriver drv = (MockDriver) z.getDriverFactory().getDriver("test");
		drv.queries.put("select * from tbl", new Result(0, new String []{"hello world"}));
		
		ZQLJob sess = jm.create();
		jm.setZql(sess.getId(), "select * from tbl");
		jm.setCron(sess.getId(), "0/1 * * * * ?");

		while (jm.get(sess.getId()).getStatus()!=Status.FINISHED){
			Thread.sleep(300);
		}
		
		List<Result> ret = (List<Result>) jm.get(sess.getId()).getReturn();
		assertEquals("hello world", ret.get(0).getRows().get(0)[0]);

		Date firstDateFinished = jm.get(sess.getId()).getDateFinished();
		
		// wait for second run
		while (jm.get(sess.getId()).getDateFinished().getTime()==firstDateFinished.getTime()){
			Thread.sleep(300);
		}
		
		ret = (List<Result>) jm.get(sess.getId()).getReturn();
		assertEquals("hello world", ret.get(0).getRows().get(0)[0]);		
		
		jm.delete(sess.getId());
	}

    public void testUTF8() throws InterruptedException, SchedulerException{
		// Create
		ZQLJob sess = jm.create();
		jm.setZql(sess.getId(), "한글");

		// run the session
		jm.run(sess.getId());
		

		while(jm.get(sess.getId()).getStatus()!=Status.FINISHED){
			Thread.sleep(300);
		}
		
		assertEquals(1, jm.list().size());
		
		jm = new ZQLJobManager(z, fs, schedulerFactory.createOrGetFIFOScheduler("analyze"), z.getConf().getString(ConfVars.ZEPPELIN_JOB_DIR));
		assertEquals(1, jm.list().size());
		assertEquals("한글",jm.list().firstEntry().getValue().getZQL());
	}
}
