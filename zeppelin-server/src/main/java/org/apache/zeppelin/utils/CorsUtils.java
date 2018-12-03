package org.apache.zeppelin.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;

public class CorsUtils {
  public static Boolean isValidOrigin(String sourceHost, ZeppelinConfiguration conf)
      throws UnknownHostException, URISyntaxException {

    String sourceUriHost = "";

    if (sourceHost != null && !sourceHost.isEmpty()) {
      sourceUriHost = new URI(sourceHost).getHost();
      sourceUriHost = (sourceUriHost == null) ? "" : sourceUriHost.toLowerCase();
    }

    sourceUriHost = sourceUriHost.toLowerCase();
    String currentHost = InetAddress.getLocalHost().getHostName().toLowerCase();

    return conf.getAllowedOrigins().contains("*")
        || currentHost.equals(sourceUriHost)
        || "localhost".equals(sourceUriHost)
        || conf.getAllowedOrigins().contains(sourceHost);
  }
}
