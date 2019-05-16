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

import org.apache.commons.lang.StringUtils;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

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

  // 1) Multiple NetworkCard will be configured on some servers
  // 2) In the docker container environment, A container will also generate multiple virtual NetworkCard
  public static String findAvailableHostAddress() throws UnknownHostException, SocketException {
    List<NetworkInterface> netlist = new ArrayList<NetworkInterface>();

    // Get all the network cards in the current environment
    Enumeration<?> netInterfaces = NetworkInterface.getNetworkInterfaces();
    while (netInterfaces.hasMoreElements()) {
      NetworkInterface networkInterface = (NetworkInterface)netInterfaces.nextElement();
      LOGGER.info("networkInterface = " + networkInterface.toString());
      if (networkInterface.isLoopback()) {
        // Filter lo network card
        continue;
      }
      // When all the network cards are obtained by the above method,
      // The order obtained is the reverse of the order of the NICs
      // seen in the server with the ifconfig command.
      // Therefore, when you want to traverse from the first NIC,
      // Need to reverse the elements in Enumeration<?>
      netlist.add(0, networkInterface);
    }

    for (NetworkInterface list:netlist) {
      Enumeration<?> enumInetAddress = list.getInetAddresses();

      while (enumInetAddress.hasMoreElements()) {
        InetAddress ip = (InetAddress) enumInetAddress.nextElement();
        LOGGER.info("ip = " + ip.toString());
        if (!ip.isLoopbackAddress() && !ip.isLinkLocalAddress()) {
          if (ip.getHostAddress().equalsIgnoreCase("127.0.0.1")){
            continue;
          }
          if (ip instanceof Inet6Address) {
            // Filter ipv6 address
            continue;
          }
          if (ip instanceof Inet4Address) {
            // Return ipv4 address
            return ip.getHostAddress();
          }
        }
        return ip.getLocalHost().getHostAddress();
      }
    }

    LOGGER.error("Can't find available HostAddress!");
    throw new UnknownHostException("Can't find available HostAddress!");
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
      int indexOfColon = intpGrpId.indexOf("-");
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
