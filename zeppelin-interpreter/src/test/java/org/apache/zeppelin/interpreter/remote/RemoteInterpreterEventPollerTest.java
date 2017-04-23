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

package org.apache.zeppelin.interpreter.remote;

import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEvent;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.junit.Test;

import static org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEventType.NO_OP;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoteInterpreterEventPollerTest {

	@Test
	public void shouldClearUnreadEventsOnShutdown() throws Exception {
		RemoteInterpreterProcess interpreterProc = getMockEventsInterpreterProcess();
		RemoteInterpreterEventPoller eventPoller = new RemoteInterpreterEventPoller(null, null);

		eventPoller.setInterpreterProcess(interpreterProc);
		eventPoller.shutdown();
		eventPoller.start();
		eventPoller.join();

		assertEquals(NO_OP, interpreterProc.getClient().getEvent().getType());
	}

	private RemoteInterpreterProcess getMockEventsInterpreterProcess() throws Exception {
		RemoteInterpreterEvent fakeEvent = new RemoteInterpreterEvent();
		RemoteInterpreterEvent noMoreEvents = new RemoteInterpreterEvent(NO_OP, "");
		RemoteInterpreterService.Client client = mock(RemoteInterpreterService.Client.class);
		RemoteInterpreterProcess intProc = mock(RemoteInterpreterProcess.class);

		when(client.getEvent()).thenReturn(fakeEvent, fakeEvent, noMoreEvents);
		when(intProc.getClient()).thenReturn(client);

		return intProc;
	}
}
