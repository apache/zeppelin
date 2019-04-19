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
package org.apache.zeppelin.serving;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * RestApiServerTest.
 */
public class RestApiServerTest {

  private RemoteInterpreterServer server;
  private HttpClient client;

  @Before
  public void setUp() throws TException, IOException {
    server = new RemoteInterpreterServer("localhost",
            RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":", "groupId", 0,true);
    server.setIntpEventClient(mock(RemoteInterpreterEventClient.class));

    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("zeppelin.interpreter.localRepo", "/tmp");

    server.createInterpreter("group_1", "session_1", RestApiTestInterpreter.class.getName(),
            intpProperties, "user_1");
    RestApiTestInterpreter interpreter1 = (RestApiTestInterpreter)
            ((LazyOpenInterpreter) server.getInterpreterGroup().get("session_1").get(0))
                    .getInnerInterpreter();

    final RemoteInterpreterContext intpContext = new RemoteInterpreterContext();
    intpContext.setNoteId("note_1");
    intpContext.setParagraphId("paragraph_1");
    intpContext.setLocalProperties(new HashMap<>());
    client = new HttpClient(new MultiThreadedHttpConnectionManager());
  }

  @After
  public void tearDown() throws TException {
    server.shutdown();
  }

  @Test
  public void testAddRestApiAccessEndpoint() throws IOException, TException, InterruptedException {
    // given
    final RemoteInterpreterContext intpContext = new RemoteInterpreterContext();
    intpContext.setNoteId("note_1");
    intpContext.setParagraphId("paragraph_1");
    intpContext.setGui("{}");
    intpContext.setNoteGui("{}");
    intpContext.setLocalProperties(new HashMap<>());

    // when accessing correct endpoint
    server.interpret("session_1", RestApiTestInterpreter.class.getName(), "ep1", intpContext);
    waitForRestApiEndpointAvailable();

    // then
    GetMethod get = new GetMethod(String.format("http://localhost:%d/%s", RestApiServer.PORT, "ep1"));
    int code = client.executeMethod(get);
    assertEquals(200, code);
    get.releaseConnection();

    // when accessing invalid endpoint
    get = new GetMethod(String.format("http://localhost:%d/%s", RestApiServer.PORT, "ep2"));

    code = client.executeMethod(get);
    assertEquals(404, code);
  }

  public static class RestApiTestInterpreter extends Interpreter {

    public RestApiTestInterpreter(Properties properties) {
      super(properties);
    }

    @Override
    public void open() {
    }

    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {
      String endpoint = st;
      context.addRestApi(endpoint, new RestApiHandler() {

        @Override
        protected void handle(HttpServletRequest request, HttpServletResponse response) {
          response.setStatus(HttpServletResponse.SC_OK);
        }
      });
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }

    @Override
    public void cancel(InterpreterContext context) throws InterpreterException {
    }

    @Override
    public FormType getFormType() throws InterpreterException {
      return FormType.NATIVE;
    }

    @Override
    public int getProgress(InterpreterContext context) throws InterpreterException {
      return 0;
    }

    @Override
    public void close() {
    }

  }

  private void waitForRestApiEndpointAvailable() throws IOException {
    long start = System.currentTimeMillis();

    while (System.currentTimeMillis() - start < 10 * 1000) {
      if (RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost", RestApiServer.PORT)) {
        return;
      } else {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
        }
      }
    }

    throw new IOException("Can't access rest api endpoint");
  }
}
