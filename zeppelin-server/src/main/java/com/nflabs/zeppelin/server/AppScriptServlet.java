package com.nflabs.zeppelin.server;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;

import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
 
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Simple servlet to dynamically set the Websocket port
 * in the JavaScript sent to the client
 */
public class AppScriptServlet extends DefaultServlet {

  // Hash containing the possible scripts that contain the getPort()
  // function originally defined in app.js
  private static Set<String> scriptPaths = new HashSet<String>(
    Arrays.asList(
      "/scripts/scripts.js",
      "/scripts/app.js"
    )
  );

  private int websocketPort;

  public AppScriptServlet(int websocketPort) {
    this.websocketPort = websocketPort;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException,
          IOException {
    
    // Process all requests not for the app script to the parent
    // class
    String uri = request.getRequestURI();
    if (!scriptPaths.contains(uri)) {
      super.doGet(request, response);
      return;
    }

    // Read the script file chunk by chunk
    Resource scriptFile = getResource(uri);
    InputStream is = scriptFile.getInputStream();
    StringBuffer script = new StringBuffer();
    byte[] buffer = new byte[1024];
    while (is.available() > 0) {
      int numRead = is.read(buffer);
      if (numRead <= 0) {
        break;
      }
      script.append(new String(buffer, 0, numRead, "UTF-8"));
    }

    // Replace the string "function getPort(){...}" to return
    // the proper value
    int startIndex = script.indexOf("function getPort()");
    int endIndex = script.indexOf("}", startIndex);

    if (startIndex >= 0 && endIndex >= 0) {
      String replaceString = "function getPort(){return " + websocketPort + "}";
      script.replace(startIndex, endIndex + 1, replaceString);
    }

    response.getWriter().println(script.toString());
  }
}

