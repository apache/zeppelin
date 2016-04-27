package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.protocol;

/**
 * Zeppelinhub Op.
 */
public enum ZeppelinHubOp {
  ALIVE,
  DEAD,
  PING,
  PONG,
  RUN_NOTEBOOK,
  WELCOME,
  ZEPPELIN_STATUS
}
