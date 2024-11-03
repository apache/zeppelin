package org.apache.zeppelin.shell.terminal;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.zeppelin.shell.terminal.websocket.TerminalSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminalCsrfTokenManager {
  private static TerminalCsrfTokenManager instance;

  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSocket.class);

  private final Map<String, String> csrfTokens = new ConcurrentHashMap<>();


  public static synchronized TerminalCsrfTokenManager getInstance(){
    if (instance == null) {
      instance = new TerminalCsrfTokenManager();
    }
    return instance;
  }

  public String generateToken(String noteId, String paragraphId) {
    String key = formatId(noteId, paragraphId);
    return csrfTokens.computeIfAbsent(key, k -> UUID.randomUUID().toString());
  }

  public boolean validateToken(String noteId, String paragraphId, String token) {
    if (token == null) {
      LOGGER.warn("Received null CSRF token for validation");
      return false;
    }

    String storedToken = csrfTokens.get(formatId(noteId, paragraphId));
    return Objects.equals(storedToken, token);
  }

  private String formatId(String noteId, String paragraphId) {
    return noteId + "@" + paragraphId;
  }
}
