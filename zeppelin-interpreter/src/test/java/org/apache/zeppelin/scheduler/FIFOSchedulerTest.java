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

package org.apache.zeppelin.scheduler;

import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.scheduler.Job.Status;

import junit.framework.TestCase;

public class FIFOSchedulerTest extends TestCase {

	private SchedulerFactory schedulerSvc;

	@Override
  public void setUp() throws Exception{
		schedulerSvc = new SchedulerFactory();
	}

	@Override
  public void tearDown(){

	}

	public void testRun() throws InterruptedException{
		Scheduler s = schedulerSvc.createOrGetFIFOScheduler("test");
		assertEquals(0, s.getJobsRunning().size());
		assertEquals(0, s.getJobsWaiting().size());

		Job job1 = new SleepingJob("job1", null, 500);
		Job job2 = new SleepingJob("job2", null, 500);

		s.submit(job1);
		s.submit(job2);
		Thread.sleep(200);

		assertEquals(Status.RUNNING, job1.getStatus());
		assertEquals(Status.PENDING, job2.getStatus());
		assertEquals(1, s.getJobsRunning().size());
		assertEquals(1, s.getJobsWaiting().size());


		Thread.sleep(500);
		assertEquals(Status.FINISHED, job1.getStatus());
		assertEquals(Status.RUNNING, job2.getStatus());
		assertTrue((500 < (Long)job1.getReturn()));
		assertEquals(1, s.getJobsRunning().size());
		assertEquals(0, s.getJobsWaiting().size());

	}

	public void testAbort() throws InterruptedException{
		Scheduler s = schedulerSvc.createOrGetFIFOScheduler("test");
		assertEquals(0, s.getJobsRunning().size());
		assertEquals(0, s.getJobsWaiting().size());

		Job job1 = new SleepingJob("job1", null, 500);
		Job job2 = new SleepingJob("job2", null, 500);

		s.submit(job1);
		s.submit(job2);

		Thread.sleep(200);

		job1.abort();
		job2.abort();

		Thread.sleep(200);

		assertEquals(Status.ABORT, job1.getStatus());
		assertEquals(Status.ABORT, job2.getStatus());

		assertTrue((500 > (Long)job1.getReturn()));
		assertEquals(null, job2.getReturn());
	}

	 public void testRemoveFromWaitingQueue() throws InterruptedException{
	    Scheduler s = schedulerSvc.createOrGetFIFOScheduler("test");
	    assertEquals(0, s.getJobsRunning().size());
	    assertEquals(0, s.getJobsWaiting().size());

	    Job job1 = new SleepingJob("job1", null, 500);
	    Job job2 = new SleepingJob("job2", null, 500);

	    s.submit(job1);
	    s.submit(job2);

	    Thread.sleep(200);

	    job1.abort();
	    job2.abort();

	    Thread.sleep(200);

	    assertEquals(Status.ABORT, job1.getStatus());
	    assertEquals(Status.ABORT, job2.getStatus());

	    assertTrue((500 > (Long)job1.getReturn()));
	    assertEquals(null, job2.getReturn());
	  }
}