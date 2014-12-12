package com.nflabs.zeppelin.server;

import java.io.InputStream;
import java.io.IOException;
 
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;

/**
 * Simple servlet to dynamically set the Websocket port
 * in the JavaScript sent to the client
 */
public class AppScriptServlet extends DefaultServlet {

  private int port;
  private String scriptPath;

  public AppScriptServlet(int port, String scriptPath) {
    this.port = port;
    this.scriptPath = scriptPath;
  }

  protected void doGet(
    HttpServletRequest request,
    HttpServletResponse response
  ) throws
      ServletException,
      IOException 
  {
    // Process all requests not for the app script to the parent
    // class
    String uri = request.getRequestURI();
    if (scriptPath == null || !scriptPath.equals(uri)) {
      super.doGet(request, response);
      return;
    }

    // Read the script file chunk by chunk
    Resource scriptFile = getResource(scriptPath);
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
      String replaceString = "function getPort(){return " + port + "}";
      script.replace(startIndex, endIndex + 1, replaceString);
    }

    response.getWriter().println(script.toString());

  }
}

