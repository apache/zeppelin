package org.apache.zeppelin.server;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.apache.zeppelin.realm.AuthorizeHeaderToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.Optional;

public class ShiroKerberosAuthenticationFilter extends ShiroFilter {

  private static final Logger LOG =
      LoggerFactory.getLogger(ShiroKerberosAuthenticationFilter.class);

  /**
   * From http://tools.ietf.org/html/rfc4559.
   */
  public static final String NEGOTIATE = "Negotiate";

  protected void handleUnauthenticated(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    sendChallenge(response);
  }

  private void sendChallenge(HttpServletResponse response) throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, NEGOTIATE);
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Override
  protected void doFilterInternal(ServletRequest request,
                                  ServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    Optional<String> authorizationHeaderValue =
        Optional.ofNullable(httpRequest.getHeader(HttpHeaders.AUTHORIZATION));
    if (authorizationHeaderValue.isPresent()) {
      LOG.debug("Authorization header is present");
      AuthorizeHeaderToken token;
      try {
        token = new AuthorizeHeaderToken(authorizationHeaderValue.get());
      } catch (IllegalArgumentException e) {
        LOG.info("Malformed Authorize header: " + e.getMessage());
        httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      try {
        SecurityUtils.getSubject().login(token);
        super.doFilterInternal(httpRequest, httpResponse, chain);
      } catch (AuthenticationException e) {
        LOG.warn("Login failed: " + e.getMessage());
        sendChallenge(httpResponse);
      }
    } else {
      LOG.debug("Authorization header is not present");
      super.doFilterInternal(httpRequest, httpResponse, chain);
//      handleUnauthenticated(httpRequest, httpResponse, chain);
    }
  }
}
