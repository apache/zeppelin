package org.apache.zeppelin.job;

import java.util.Date;

import org.apache.zeppelin.job.JobId;

import junit.framework.TestCase;

public class JobIdTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void testDate(){
		JobId id = new JobId();
		Date d = id.getDate();
		assertTrue(System.currentTimeMillis() - d.getTime() < 500);
		
	}

}
