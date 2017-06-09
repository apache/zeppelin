package org.apache.zeppelin.server;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This servlet redirects the user back to the application root
 * after a successful indirect login.  This is a browser addressable link,
 * not a restful web service.
 */
public class IndirectLoginServlet extends HttpServlet {

  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response) 
      throws ServletException, IOException {
    response.sendRedirect(request.getContextPath());
  }

}
