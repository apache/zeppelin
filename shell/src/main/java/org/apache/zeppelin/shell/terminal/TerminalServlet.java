package org.apache.zeppelin.shell.terminal;

import com.google.common.io.Resources;
import com.hubspot.jinjava.Jinjava;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminalServlet extends HttpServlet {

  private Jinjava jinjava = new Jinjava();

  @Override
  protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response
  )
      throws ServletException, IOException {
    URL urlTemplate = Resources.getResource("ui_templates/terminal-ui.jinja");
    String template = Resources.toString(urlTemplate, StandardCharsets.UTF_8);

    String noteId = request.getParameter("noteId");
    String paragraphId = request.getParameter("paragraphId");

    String csrfToken = TerminalCsrfTokenManager.getInstance().generateToken(noteId, paragraphId);

    Map<String, Object> context = new HashMap<>();
    context.put("CSRF_TOKEN", csrfToken);

    String renderedTemplate = jinjava.render(template, context);

    response.setContentType("text/html; charset=UTF-8");
    response.setStatus(HttpServletResponse.SC_OK);
    response.getWriter().write(renderedTemplate);
  }
}
