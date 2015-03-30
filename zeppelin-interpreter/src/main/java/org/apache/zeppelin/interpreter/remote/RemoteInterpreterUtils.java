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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 */
public class RemoteInterpreterUtils {
  public static int findRandomAvailablePortOnAllLocalInterfaces() throws IOException {
    int port;
    try (ServerSocket socket = new ServerSocket(0);) {
      port = socket.getLocalPort();
      socket.close();
    }
    return port;
  }

  public static boolean checkIfRemoteEndpointAccessible(String host, int port) {
    try {
      Socket discover = new Socket();
      discover.setSoTimeout(1000);
      discover.connect(new InetSocketAddress(host, port), 1000);
      discover.close();
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
