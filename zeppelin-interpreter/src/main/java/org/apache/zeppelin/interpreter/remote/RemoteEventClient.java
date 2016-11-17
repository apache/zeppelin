package org.apache.zeppelin.interpreter.remote;

import java.util.Map;

/**
 * 
 * Wrapper arnd RemoteInterpreterEventClient
 * to expose methods in the client
 *
 */
public class RemoteEventClient implements RemoteEventClientWrapper {

  private RemoteInterpreterEventClient client;

  public RemoteEventClient(RemoteInterpreterEventClient client) {
    this.client = client;
  }

  @Override
  public void onMetaInfosReceived(Map<String, String> infos) {
    client.onMetaInfosReceived(infos);
  }

}
