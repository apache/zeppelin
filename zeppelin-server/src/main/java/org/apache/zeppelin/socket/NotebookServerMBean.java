package org.apache.zeppelin.socket;

import java.util.Set;

public interface NotebookServerMBean {
  Set<String> getConnectedUsers();

  void sendMessage(String message);
}
