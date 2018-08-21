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

package org.apache.zeppelin.python;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Properties;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;

public class PythonUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(PythonUtils.class);

  public static GatewayServer createGatewayServer(
      Object entryPoint, String serverAddress, int port, String secretKey, boolean useAuth)
      throws IOException {
    LOGGER.info(
        "Launching GatewayServer at " + serverAddress + ":" + port + ", useAuth: " + useAuth);
    if (useAuth) {
      try {
        Class clz =
            Class.forName(
                "py4j.GatewayServer$GatewayServerBuilder",
                true,
                Thread.currentThread().getContextClassLoader());
        Object builder = clz.getConstructor(Object.class).newInstance(entryPoint);
        builder.getClass().getMethod("authToken", String.class).invoke(builder, secretKey);
        builder.getClass().getMethod("javaPort", int.class).invoke(builder, port);
        builder
            .getClass()
            .getMethod("javaAddress", InetAddress.class)
            .invoke(builder, InetAddress.getByName(serverAddress));
        builder
            .getClass()
            .getMethod("callbackClient", int.class, InetAddress.class, String.class)
            .invoke(builder, port, InetAddress.getByName(serverAddress), secretKey);
        return (GatewayServer) builder.getClass().getMethod("build").invoke(builder);
      } catch (Exception e) {
        throw new IOException(e);
      }
    } else {
      return new GatewayServer(
          entryPoint,
          port,
          GatewayServer.DEFAULT_PYTHON_PORT,
          InetAddress.getByName(serverAddress),
          InetAddress.getByName(serverAddress),
          GatewayServer.DEFAULT_CONNECT_TIMEOUT,
          GatewayServer.DEFAULT_READ_TIMEOUT,
          (List) null);
    }
  }

  public static String getLocalIP(Properties properties) {
    // zeppelin.python.gatewayserver_address is only for unit test on travis.
    // Because the FQDN would fail unit test on travis ci.
    String gatewayserver_address = properties.getProperty("zeppelin.python.gatewayserver_address");
    if (gatewayserver_address != null) {
      return gatewayserver_address;
    }

    try {
      return Inet4Address.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      LOGGER.warn("can't get local IP", e);
    }
    // fall back to loopback addreess
    return "127.0.0.1";
  }

  public static String createSecret(int secretBitLength) {
    SecureRandom rnd = new SecureRandom();
    byte[] secretBytes = new byte[secretBitLength / java.lang.Byte.SIZE];
    rnd.nextBytes(secretBytes);
    return Base64.encodeBase64String(secretBytes);
  }
}
