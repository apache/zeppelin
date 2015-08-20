package org.apache.zeppelin.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates given host name is allowed 
 */
public class OriginValidator {
  Logger logger = LoggerFactory.getLogger(OriginValidator.class);

  private ZeppelinConfiguration conf;
  private final List<String> allowedOrigins;
  private final String ALLOW_ALL = "*";

  public OriginValidator(ZeppelinConfiguration conf) {
    this.conf = conf;
    allowedOrigins = new LinkedList<String>();
    initAllowedOrigins();
  }
  
  /**
   * 
   * @param origin origin to check
   * @return matched origin. null when it's not allowed
   */
  public String validate(String origin) {
    try {
      // just get host if origin is form of URI
      URI sourceUri = new URI(origin);
      String sourceHost = sourceUri.getHost();
      if (sourceHost != null && !sourceHost.isEmpty()) {
        origin = sourceHost;
      }
    } catch (URISyntaxException e) {
      // we can silently ignore this error
    }
    
    if (origin == null) {
      return null;
    }

    for (String p : allowedOrigins) {
      if (p == null || p.trim().length() == 0) {
        continue;
      }
      if (p.trim().compareToIgnoreCase(origin) == 0 || p.trim().equals(ALLOW_ALL)) {
        return p.trim();
      }      
    }
    return null;
  }
  
  private void initAllowedOrigins() {
    String currentHost;
    try {
      currentHost = java.net.InetAddress.getLocalHost().getHostName();
      allowedOrigins.add(currentHost);
    } catch (UnknownHostException e) {
      logger.error("Can't get hostname", e);
    }
    
    
    String origins = conf.getString(ConfVars.ZEPPELIN_SERVER_ORIGINS);
    if (origins == null || origins.length() == 0) {
      return;
    } else {
      for (String origin : origins.split(",")) {
        allowedOrigins.add(origin);
      }
    }
  }
}
