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

import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

/**
 *
 */
public class RemoteInterpreterUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterUtils.class);

  private RemoteInterpreterUtils() {
    throw new IllegalStateException("Utility class");
  }

  public static int findRandomAvailablePortOnAllLocalInterfaces() throws IOException {
    int port;
    try (ServerSocket socket = new ServerSocket(0);) {
      port = socket.getLocalPort();
    }
    return port;
  }

  /**
   * start:end
   *
   * @param portRange
   * @return
   * @throws IOException
   */
  public static TServerSocket createTServerSocket(String portRange)
      throws IOException {

    TServerSocket tSocket = null;
    // ':' is the default value which means no constraints on the portRange
    if (StringUtils.isBlank(portRange) || portRange.equals(":")) {
      try {
        tSocket = new TServerSocket(0);
        return tSocket;
      } catch (TTransportException e) {
        throw new IOException("Fail to create TServerSocket", e);
      }
    }
    // valid user registered port https://en.wikipedia.org/wiki/Registered_port
    int start = 1024;
    int end = 65535;
    String[] ports = portRange.split(":", -1);
    if (!ports[0].isEmpty()) {
      start = Integer.parseInt(ports[0]);
    }
    if (!ports[1].isEmpty()) {
      end = Integer.parseInt(ports[1]);
    }
    for (int i = start; i <= end; ++i) {
      try {
        tSocket = new TServerSocket(i);
        return tSocket;
      } catch (Exception e) {
        // ignore this
      }
    }
    throw new IOException("No available port in the portRange: " + portRange);
  }

  public static String findAvailableHostAddress() throws UnknownHostException, SocketException {
    String zeppelinServerIP = System.getenv("ZEPPELIN_LOCAL_IP");
    if (zeppelinServerIP != null) {
      return zeppelinServerIP;
    }

    InetAddress address = InetAddress.getLocalHost();
    if (address.isLoopbackAddress()) {
      for (NetworkInterface networkInterface : Collections
          .list(NetworkInterface.getNetworkInterfaces())) {
        if (!networkInterface.isLoopback()) {
          for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
            InetAddress a = interfaceAddress.getAddress();
            if (a instanceof Inet4Address) {
              return a.getHostAddress();
            }
          }
        }
      }
    }
    return address.getHostAddress();
  }

  public static boolean checkIfRemoteEndpointAccessible(String host, int port) {
    try (Socket discover = new Socket()) {
      discover.setSoTimeout(1000);
      discover.connect(new InetSocketAddress(host, port), 1000);
      return true;
    } catch (IOException e) {
      // end point is not accessible
      LOGGER.debug("Remote endpoint '{}:{}' is not accessible " +
            "(might be initializing): {}" , host, port, e.getMessage());
      return false;
    }
  }

  public static String getInterpreterSettingId(String intpGrpId) {
    String settingId = null;
    if (intpGrpId != null) {
      int indexOfColon = intpGrpId.indexOf('-');
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
