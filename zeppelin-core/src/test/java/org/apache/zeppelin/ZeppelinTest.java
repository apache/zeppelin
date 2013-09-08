package org.apache.zeppelin;

import java.io.File;
import java.io.IOException;

import org.apache.zeppelin.Zeppelin;
import org.apache.zeppelin.job.Job;
import org.apache.zeppelin.job.JobId;
import org.apache.zeppelin.job.JobInfo;
import org.apache.zeppelin.job.JobRunner;
import org.apache.zeppelin.job.JobRunner.Status;

import junit.framework.TestCase;

public class ZeppelinTest extends TestCase {

	private File tmpPath;

	protected void setUp() throws Exception {
		super.setUp();
		String tempDir = System.getProperty("java.io.tmpdir")+"/zeppelin_test_"+System.currentTimeMillis();
		tmpPath = new File(tempDir);
		tmpPath.mkdirs();
	}

	protected void tearDown() throws Exception {
		deleteRecursive(tmpPath);
		super.tearDown();
	}
	
	private void deleteRecursive(File file){
		if(file.isDirectory()){
			for(File f : file.listFiles()){
				deleteRecursive(f);
			}
			file.delete();
		} else if(file.isFile()){
			file.delete();
		}
	}

	public void testZeppelinDriverLoading() throws InterruptedException, IOException{
		Zeppelin zp = new Zeppelin(tmpPath.getAbsolutePath());
		Job job = new Job(new JobId(), "hello world", null);
		JobId id = zp.submit(job, "org.apache.zeppelin.DummyDriver");
		JobInfo jobInfo = zp.getJobInfo(id);
		assertTrue(jobInfo.getStatus()==JobRunner.Status.CREATED || jobInfo.getStatus()==JobRunner.Status.RUNNING );
		Thread.sleep(500);
		
		jobInfo = zp.getJobInfo(id);
		assertNotNull(jobInfo.getProgress());
		
		Thread.sleep(700);
		jobInfo = zp.getJobInfo(id);
		assertEquals(Status.FINISHED, jobInfo.getStatus());
	}
}
