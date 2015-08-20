/**
 * Created by joelz on 8/6/15.
 *
 *
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
package org.apache.zeppelin.socket;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.server.OriginValidator;
import org.junit.Assert;
import org.junit.Test;

import java.net.UnknownHostException;

/**
 * BASIC Zeppelin rest api tests
 *
 *
 * @author joelz
 *
 */
public class NotebookServerTests {

  @Test
  public void CheckOrigin() throws UnknownHostException {
    System.setProperty(ConfVars.ZEPPELIN_SERVER_ORIGINS.getVarName(), "");

    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    OriginValidator originValidator = new OriginValidator(conf);
    NotebookServer server = new NotebookServer(originValidator);
    Assert.assertTrue(server
        .checkOrigin(new TestHttpServletRequest(), "http://"
            + java.net.InetAddress.getLocalHost().getHostName() + ":8080"));
  }

  @Test
  public void CheckInvalidOrigin() {
    System.setProperty(ConfVars.ZEPPELIN_SERVER_ORIGINS.getVarName(), "");

    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    OriginValidator originValidator = new OriginValidator(conf);
    NotebookServer server = new NotebookServer(originValidator);
    Assert.assertFalse(server.checkOrigin(new TestHttpServletRequest(),
        "http://evillocalhost:8080"));
  }
}
