package com.nflabs.zeppelin.interpreter.remote;

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
