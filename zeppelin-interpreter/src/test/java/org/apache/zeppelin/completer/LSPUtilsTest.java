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

package org.apache.zeppelin.completer;

import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.Socket;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;


@RunWith(PowerMockRunner.class)
@PrepareForTest(LSPUtils.class)
public class LSPUtilsTest {
  private static final String HOST = "localhost";
  private static final int PORT = 2087;
  private static final String BUF = "p";
  private static final String LANG_ID = "python";
  private static final int CURSOR = 0;

  @Test
  public void getLspServerCompletionTest() throws Exception {

    PowerMockito.mockStatic(LSPUtils.class);
    PowerMockito.whenNew(Socket.class).withAnyArguments().thenReturn(null);
    PowerMockito.when(LSPUtils.connectToLanguageServer(any(Socket.class)))
        .thenReturn(new MockLanguageServer());

    PowerMockito.when(LSPUtils.castCompletionItemToInterpreterCompletion(any()))
        .thenCallRealMethod();
    PowerMockito.when(LSPUtils.castList(any())).thenCallRealMethod();
    PowerMockito.when(LSPUtils.eitherToCompletionList(any())).thenCallRealMethod();
    PowerMockito.when(LSPUtils.getLspServerCompletion(anyString(), anyInt(), anyString(), anyInt(),
        anyString())).thenCallRealMethod();

    List<InterpreterCompletion> res = LSPUtils
        .getLspServerCompletion(BUF, CURSOR, HOST, PORT, LANG_ID);

    System.out.println(res);

    assertTrue(res.size() > 0);
    assertEquals("print", res.get(0).name);
  }
}
