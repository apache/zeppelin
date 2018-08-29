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

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer.InterpretJob;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class JobTest {

  @Mock private JobListener mockJobListener;
  @Mock private Interpreter mockInterpreter;
  @Mock private InterpreterContext mockInterpreterContext;
  private InterpretJob spyInterpretJob;

  @Before
  public void setUp() throws Exception {
    InterpretJob interpretJob =
        new InterpretJob(
            "jobid",
            "jobName",
            mockJobListener,
            mockInterpreter,
            "script",
            mockInterpreterContext);
    spyInterpretJob = spy(interpretJob);
  }

  @Test
  public void testNormalCase() throws Throwable {

    InterpreterResult successInterpreterResult =
        new InterpreterResult(Code.SUCCESS, "success result");
    doReturn(successInterpreterResult).when(spyInterpretJob).jobRun();

    spyInterpretJob.run();

    assertEquals(successInterpreterResult, spyInterpretJob.getReturn());
  }

  @Test
  public void testErrorCase() throws Throwable {
    String failedMessage = "failed message";
    InterpreterException interpreterException = new InterpreterException(failedMessage);
    doThrow(interpreterException).when(spyInterpretJob).jobRun();

    spyInterpretJob.run();

    Object failedResult = spyInterpretJob.getReturn();
    assertNull(failedResult);
    assertNotNull(spyInterpretJob.getException());
  }
}
