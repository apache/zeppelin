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
package org.apache.zeppelin.server;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.junit.jupiter.api.Test;

class JettyServernameTest {
  private final String headerValue = "ZeppelinApache";
  @Test
  void defaultConfiguration() {
    // given
    Request request = mock(Request.class);
    Response response = mock(Response.class);
    when(request.getResponse()).thenReturn(response);
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    JettyServername httpListener = new JettyServername(zConf);
    // when
    httpListener.onResponseBegin(request);
    // then
    verifyNoInteractions(response);
  }

  @Test
  void DoNotSendServerName() {
    // given
    Request request = mock(Request.class);
    Response response = mock(Response.class);
    when(request.getResponse()).thenReturn(response);
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ConfVars.ZEPPELIN_SERVER_SEND_JETTY_NAME.getVarName(), "false");
    zConf.setProperty(ConfVars.ZEPPELIN_SERVER_JETTY_NAME.getVarName(), headerValue);
    JettyServername httpListener = new JettyServername(zConf);
    // when
    httpListener.onResponseBegin(request);
    // then
    verifyNoInteractions(response);
  }

  @Test
  void SendServerName() {
    // given
    Request request = mock(Request.class);
    Response response = mock(Response.class);
    when(request.getResponse()).thenReturn(response);
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ConfVars.ZEPPELIN_SERVER_SEND_JETTY_NAME.getVarName(), "true");
    zConf.setProperty(ConfVars.ZEPPELIN_SERVER_JETTY_NAME.getVarName(), headerValue);
    JettyServername httpListener = new JettyServername(zConf);
    // when
    httpListener.onResponseBegin(request);
    // then
    verify(response).setHeader(HttpHeader.SERVER, headerValue);
  }
}
