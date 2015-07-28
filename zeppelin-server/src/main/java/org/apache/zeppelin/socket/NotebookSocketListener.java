package org.apache.zeppelin.socket;

/**
 * NoteboookSocket listener
 */
public interface NotebookSocketListener {
  public void onClose(NotebookSocket socket, int code, String message);
  public void onOpen(NotebookSocket socket);
  public void onMessage(NotebookSocket socket, String message);
}
