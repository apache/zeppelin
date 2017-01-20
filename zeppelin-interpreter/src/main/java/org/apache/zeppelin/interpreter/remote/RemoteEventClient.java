package org.apache.zeppelin.interpreter.remote;

import java.util.HashMap;
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

  @Override
  public void onParaInfosReceived(String noteId, String paragraphId, Map<String, String> infos) {
    Map<String, String> paraInfos =  new HashMap<String, String>(infos);
    paraInfos.put("noteId", noteId);
    paraInfos.put("paraId", paragraphId);
    client.onParaInfosReceived(paraInfos);
  }


}
