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
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.interpreter.thrift.CallbackInfo;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterCallbackService;

/**
 *
 */
public class RemoteInterpreterUtils {
  static Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterUtils.class);
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
    } catch (ConnectException cne) {
      // end point is not accessible
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Remote endpoint '" + host + ":" + port + "' is not accessible " +
                "(might be initializing): " + cne.getMessage());
      }
      return false;
    } catch (IOException ioe) {
      // end point is not accessible
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Remote endpoint '" + host + ":" + port + "' is not accessible " +
                "(might be initializing): " + ioe.getMessage());
      }
      return false;
    }
  }

  public static String getInterpreterSettingId(String intpGrpId) {
    String settingId = null;
    if (intpGrpId != null) {
      int indexOfColon = intpGrpId.indexOf(":");
      settingId = intpGrpId.substring(0, indexOfColon);
    }
    return settingId;
  }

  public static boolean isEnvString(String key) {
    if (key == null || key.length() == 0) {
      return false;
    }

    return key.matches("^[A-Z_0-9]*");
  }
}
