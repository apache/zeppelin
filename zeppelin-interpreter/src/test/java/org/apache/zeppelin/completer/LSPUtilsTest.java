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
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import static org.eclipse.lsp4j.jsonrpc.Launcher.createLauncher;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class LSPUtilsTest{
  private static final String HOST = "localhost";
  private static final String BUF = "p";
  private static final String LANG_ID = "python";
  private static final int CURSOR = 0;

  @Test
  public void getLspServerComplitionsTest() throws Exception{

    try (ServerSocket serverSocket = new ServerSocket(0)) {
      final int port = serverSocket.getLocalPort();

      Thread t = new Thread(() -> {
        Socket socket;
        try {
          while (!Thread.currentThread().isInterrupted()) {
            socket = serverSocket.accept();
            Launcher<LanguageClient> launcher = createLauncher(
                new MockLanguageServer(),
                LanguageClient.class,
                socket.getInputStream(),
                socket.getOutputStream());
            launcher.startListening();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
      t.start();

      List<InterpreterCompletion> res = LSPUtils
          .getLspServerComplitions(BUF, CURSOR, HOST, port, LANG_ID);

      assertTrue(res.size() > 0);
      assertEquals("print", res.get(0).name);
    }
  }
}
