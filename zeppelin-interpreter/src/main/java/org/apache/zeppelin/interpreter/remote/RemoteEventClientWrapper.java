package org.apache.zeppelin.interpreter.remote;

import java.util.Map;

/**
 * 
 * Wrapper interface for RemoterInterpreterEventClient
 * to expose only required methods from EventClient
 *
 */
public interface RemoteEventClientWrapper {

  public void onMetaInfosReceived(Map<String, String> infos);

  public void onParaInfosReceived(String noteId, String paragraphId,
                                            Map<String, String> infos);

}
