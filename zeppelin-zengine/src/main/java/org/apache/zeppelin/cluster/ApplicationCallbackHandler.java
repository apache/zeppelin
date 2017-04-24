package org.apache.zeppelin.cluster;

/**
 *
 */
public interface ApplicationCallbackHandler {
  void onStarted(String host, int port);

  void onTerminated(String id);
}
