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

package org.apache.zeppelin.integration;

import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ParagraphLifecycleIT extends AbstractTestRestApi {
	private static final Logger LOG = LoggerFactory.getLogger(NotebookServer.class);

	private static final String JOB_IN_PROGRESS = "job in progress";
	private static final String JOB_IS_DONE = "job is done";
	private static final String RESUME_JOB = "resume job";
	private static final String JOB_RESULT = "job result";

	private BlockingQueue<String> msgToParagraph = new SynchronousQueue<>();
	private BlockingQueue<String> msgFromParagraph = new SynchronousQueue<>();
	private Note note;
	private Paragraph mockParagraph;
	private JobRunner paragraphRunner;

	@BeforeClass
	public static void init() throws Exception {
		AbstractTestRestApi.startUp();
	}

	@AfterClass
	public static void destroy() throws Exception {
		AbstractTestRestApi.shutDown();
	}

	@Before
	public void prepareRunnerWithMockParagraph() throws IOException {
		note = ZeppelinServer.notebook.createNote(AuthenticationInfo.ANONYMOUS);
		mockParagraph = new GuidedParagraph(note, msgFromParagraph, msgToParagraph);
		note.addParagraph(mockParagraph);
		paragraphRunner = new JobRunner(mockParagraph, msgFromParagraph);
	}

	@After
	public void terminateRunningParagraph() throws InterruptedException {
		LOG.info("Terminating paragraph after test is completed");
		paragraphRunner.interrupt();
		paragraphRunner.join();
	}

	@Test
	public void shouldClearParagraphWhileRunning() throws IOException, InterruptedException {
		mockParagraph.setResult(JOB_RESULT);
		paragraphRunner.start();
		waitForJobMessage(JOB_IN_PROGRESS);
		invokeOutputClearEvent();

		assertNull(mockParagraph.getReturn());
	}

	@Test
	public void shouldNotClearParagraphAfterCompletion() throws IOException, InterruptedException {
		paragraphRunner.start();
		waitForJobMessage(JOB_IS_DONE);
		invokeOutputClearEvent();

		assertNotNull(mockParagraph.getReturn());
	}

	private void waitForJobMessage(String message) throws InterruptedException {
		while (!msgFromParagraph.take().equals(message)) {
			msgToParagraph.put(RESUME_JOB);
		}
	}

	private void invokeOutputClearEvent() {
		RemoteInterpreterProcessListener eventListener = new NotebookServer();
		eventListener.onOutputClear(note.getId(), mockParagraph.getId());
	}

	private class GuidedParagraph extends Paragraph {
		BlockingQueue<String> outgoingBox;
		BlockingQueue<String> incomingBox;

		GuidedParagraph(Note note,
						BlockingQueue<String> outgoingBox,
						BlockingQueue<String> incomingBox) {
			super("paragraphId", note, null, null, null);
			this.outgoingBox = outgoingBox;
			this.incomingBox = incomingBox;
		}

		@Override
		protected Object jobRun() throws InterruptedException {
			outgoingBox.put(JOB_IN_PROGRESS);
			while (incomingBox.take() != RESUME_JOB) {}
			return JOB_RESULT;
		}
	}

	private class JobRunner extends Thread {
		private Job job;
		private BlockingQueue<String> outgoingBox;

		JobRunner(Job job, BlockingQueue<String> outgoingBox) {
			this.job = job;
			this.outgoingBox = outgoingBox;
		}

		@Override
		public void run() {
			job.run();
			if (job.getException() == null) {
				try {
					outgoingBox.put(JOB_IS_DONE);
				} catch (InterruptedException e) {
					LOG.info("Job was interrupted", e);
				}
			}
		}
	}
}