/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.realm.kerberos;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.Groups;
import org.apache.hadoop.security.authentication.client.AuthenticatedURL;
import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.hadoop.security.authentication.client.KerberosAuthenticator;
import org.apache.hadoop.security.authentication.server.AuthenticationHandler;
import org.apache.hadoop.security.authentication.server.AuthenticationToken;
import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.security.authentication.util.*;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KeyTab;
import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The {@link KerberosRealm} implements the Kerberos SPNEGO
 * authentication mechanism for HTTP.
 * <p>
 * The supported configuration properties are:
 * <ul>
 * <li>kerberos.principal: the Kerberos principal to used by the server. As
 * stated by the Kerberos SPNEGO specification, it should be
 * <code>HTTP/${HOSTNAME}@{REALM}</code>. The realm can be omitted from the
 * principal as the JDK GSS libraries will use the realm name of the configured
 * default realm.
 * It does not have a default value.</li>
 * <li>kerberos.keytab: the keytab file containing the credentials for the
 * Kerberos principal.
 * It does not have a default value.</li>
 * <li>kerberos.name.rules: kerberos names rules to resolve principal names, see
 * {@link KerberosName#setRules(String)}</li>
 * </ul>
 */
public class KerberosRealm extends AuthorizingRealm {
  public static final Logger LOG = LoggerFactory.getLogger(KerberosRealm.class);

  /**
   * Constant for the property that specifies the configuration prefix.
   */
  private static final String CONFIG_PREFIX = "config.prefix";

  /**
   * Constant for the property that specifies the authentication handler to use.
   */
  private static final String AUTH_TYPE = "type";

  /**
   * Constant for the property that specifies the secret to use for signing the HTTP Cookies.
   */
  private static final String SIGNATURE_SECRET = "signature.secret";

  private static final String SIGNATURE_SECRET_FILE = SIGNATURE_SECRET + ".file";

  /**
   * Constant for the configuration property
   * that indicates the max inactive interval of the generated token.
   */
  private static final String AUTH_TOKEN_MAX_INACTIVE_INTERVAL = "token.max-inactive-interval";

  /**
   * Constant for the configuration property that indicates the tokenValidity of the generated
   * token.
   */
  private static final String AUTH_TOKEN_VALIDITY = "token.tokenValidity";

  /**
   * Constant for the configuration property that indicates the domain to use in the HTTP cookie.
   */
  private static final String COOKIE_DOMAIN = "cookie.domain";

  /**
   * Constant for the configuration property that indicates the path to use in the HTTP cookie.
   */
  private static final String COOKIE_PATH = "cookie.path";

  /**
   * Constant for the configuration property
   * that indicates the persistence of the HTTP cookie.
   */
  private static final String COOKIE_PERSISTENT = "cookie.persistent";

  /**
   * Constant for the configuration property that indicates the name of the
   * SignerSecretProvider class to use.
   * Possible values are: "file"
   * We are NOT supporting "random", "zookeeper", or a classname, at the moment
   * If not specified, the "file" implementation will be used with
   * SIGNATURE_SECRET_FILE; and if that's not specified, the "random"
   * implementation will be used.
   */
  private static final String SIGNER_SECRET_PROVIDER = "signer.secret.provider";

  private static Signer signer = null;
  private SignerSecretProvider secretProvider = null;
  private boolean destroySecretProvider = true;

  // Configs to set in shiro.ini
  private String signatureSecretFile = null;
  private long tokenMaxInactiveInterval = -1;
  private long tokenValidity = 36000; // 10 hours
  private String cookieDomain = null;
  private String cookiePath = "/";
  private boolean isCookiePersistent = false;
  private String principal = null;
  private String keytab = null;
  private String nameRules = "DEFAULT";

  /**
   * Constant that identifies the authentication mechanism.
   */
  public static final String TYPE = "kerberos";

  /**
   * Constant for the configuration property that indicates the kerberos
   * principal.
   */
  public static final String PRINCIPAL = TYPE + ".principal";

  /**
   * Constant for the configuration property that indicates the keytab
   * file path.
   */
  public static final String KEYTAB = TYPE + ".keytab";

  /**
   * Constant for the configuration property that indicates the Kerberos name
   * rules for the Kerberos principals.
   */
  public static final String NAME_RULES = TYPE + ".name.rules";

  private static final String type = TYPE;
  private String rules;
  private GSSManager gssManager;
  private Subject serverSubject = new Subject();
  private Properties config = null;

  /**
   * Configuration object needed by for hadoop classes
   */
  private Configuration hadoopConfig;

  /**
   * Hadoop Groups implementation.
   */
  private Groups hadoopGroups;

  @Override
  public boolean supports(org.apache.shiro.authc.AuthenticationToken token) {
    return token instanceof KerberosToken;
  }

  /**
   * Initializes the KerberosRealm by 'kinit'ing using principal and keytab.
   * <p>
   * It creates a Kerberos context using the principal and keytab specified in
   * the Shiro configuration.
   * <p>
   * This method should be called only once; is invoked in {@link KerberosAuthenticationFilter}
   *
   * @throws RuntimeException thrown if the handler could not be initialized.
   */
  @Override
  protected void onInit() {
    LOG.info("VR46 - inside onInit()");
    super.onInit();
    config = getConfiguration();
    try {
      if (principal == null || principal.trim().length() == 0) {
        throw new RuntimeException("Principal not defined in configuration");
      }

      if (keytab == null || keytab.trim().length() == 0) {
        throw new RuntimeException("Keytab not defined in configuration");
      }

      File keytabFile = new File(keytab);
      if (!keytabFile.exists()) {
        throw new RuntimeException("Keytab file does not exist: " + keytab);
      }

      // use all SPNEGO principals in the keytab if a principal isn't
      // specifically configured
      final String[] spnegoPrincipals;
      if (principal.equals("*")) {
        spnegoPrincipals = KerberosUtil.getPrincipalNames(
            keytab, Pattern.compile("HTTP/.*"));
        if (spnegoPrincipals.length == 0) {
          throw new RuntimeException("Principals do not exist in the keytab");
        }
      } else {
        spnegoPrincipals = new String[]{principal};
      }
      KeyTab keytabInstance = KeyTab.getInstance(keytabFile);
      serverSubject.getPrivateCredentials().add(keytabInstance);
      for (String spnegoPrincipal : spnegoPrincipals) {
        Principal krbPrincipal = new KerberosPrincipal(spnegoPrincipal);
        LOG.info("Using keytab {}, for principal {}",
            keytab, krbPrincipal);
        serverSubject.getPrincipals().add(krbPrincipal);
      }

      if (rules == null || rules.trim().length() == 0) {
        LOG.warn("No auth_to_local rules defined, DEFAULT will be used.");
        rules = "DEFAULT";
      }

      KerberosName.setRules(rules);

      try {
        gssManager = Subject.doAs(serverSubject,
            new PrivilegedExceptionAction<GSSManager>() {
              @Override
              public GSSManager run(){
                return GSSManager.getInstance();
              }
            });
      } catch (PrivilegedActionException ex) {
        throw ex.getException();
      }

      initializeSecretProvider();

      hadoopConfig = new Configuration();
      hadoopGroups = new Groups(hadoopConfig);

    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    LOG.info("VR46 - exit onInit()");
  }

  protected void initializeSecretProvider() throws ServletException {
    try {
      secretProvider = constructSecretProvider(false);
      destroySecretProvider = true;
      signer = new Signer(secretProvider);
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }

  public SignerSecretProvider constructSecretProvider(
      boolean disallowFallbackToRandomSecretProvider) throws Exception {
    // Default Signature provider type is file
    String name = "file";
    if (!disallowFallbackToRandomSecretProvider
        && config.getProperty(SIGNATURE_SECRET_FILE) == null) {
      name = "random";
    }

    SignerSecretProvider provider;
    if ("file".equals(name)) {
      provider = new FileSignerSecretProvider();
      try {
        provider.init(config, null, tokenValidity);
      } catch (Exception e) {
        if (!disallowFallbackToRandomSecretProvider) {
          LOG.info("Unable to initialize FileSignerSecretProvider, " +
              "falling back to use random secrets.");
          provider = new RandomSignerSecretProvider();
          provider.init(config, null, tokenValidity);
        } else {
          throw e;
        }
      }
    } else if ("random".equals(name)) {
      provider = new RandomSignerSecretProvider();
      provider.init(config, null, tokenValidity);
    } else {
      throw new RuntimeException("Custom Signer not implemented yet. Use 'file' or 'random'.");
    }
    return provider;
  }

  /**
   * This is an empty implementation, it always returns <code>TRUE</code>.
   *
   * @param token the authentication token if any, otherwise <code>NULL</code>.
   * @param request the HTTP client request.
   * @param response the HTTP client response.
   *
   * @return <code>TRUE</code>
   * @throws IOException it is never thrown.
   * @throws AuthenticationException it is never thrown.
   */
  public boolean managementOperation(AuthenticationToken token,
                                     HttpServletRequest request,
                                     HttpServletResponse response)
      throws IOException, AuthenticationException {
    return true;
  }

  @Override
  public AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals)
      throws AuthorizationException {
    Set<String> roles = mapGroupPrincipals(principals.toString());
    return new SimpleAuthorizationInfo(roles);
  }

  /**
   * Query the Hadoop implementation of {@link Groups} to retrieve groups for
   * provided user.
   */
  public Set<String> mapGroupPrincipals(final String mappedPrincipalName)
      throws AuthorizationException {
    /* return the groups as seen by Hadoop */
    Set<String> groups = null;
    try {
      hadoopGroups.refresh();
      final List<String> groupList = hadoopGroups.getGroups(mappedPrincipalName);

      LOG.debug(String.format("group found %s, %s",
            mappedPrincipalName, groupList.toString()));

      groups = new HashSet<>(groupList);

    } catch (final IOException e) {
      if (e.toString().contains("No groups found for user")) {
        /* no groups found move on */
        LOG.info(String.format("No groups found for user %s", mappedPrincipalName));
      } else {
        /* Log the error and return empty group */
        LOG.info(String.format("errorGettingUserGroups for %s", mappedPrincipalName));
        throw new AuthorizationException(e);
      }
      groups = new HashSet();
    }
    return groups;
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(
      org.apache.shiro.authc.AuthenticationToken authenticationToken)
      throws org.apache.shiro.authc.AuthenticationException {
    LOG.debug("VR46 - inside doGetAuthenticationInfo()");
    if (null != authenticationToken) {
      KerberosToken kerberosToken = (KerberosToken) authenticationToken;
      //  try {
      SimpleAccount account = new SimpleAccount(kerberosToken.getPrincipal(),
          kerberosToken.getCredentials(), kerberosToken.getClass().getName());
      //account.addRole(mapGroupPrincipals(getName(upToken)));
      return account;
      // } catch (ParseException e) {
      //   LOG.error("ParseException in doGetAuthenticationInfo", e);
      // }
    }
    return null;
  }

  /**
   * If the request has a valid authentication token it allows the request to continue to
   * the target resource,
   * otherwise it triggers a GSS-API sequence for authentication
   *
   * @param request     the request object.
   * @param response    the response object.
   * @param filterChain the filter chain object.
   * @throws IOException      thrown if an IO error occurred.
   * @throws ServletException thrown if a processing error occurred.
   */
  public void doKerberosAuth(ServletRequest request,
                             ServletResponse response,
                             FilterChain filterChain)
      throws IOException, ServletException {
    boolean unauthorizedResponse = true;
    int errCode = HttpServletResponse.SC_UNAUTHORIZED;
    AuthenticationException authenticationEx = null;
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    boolean isHttps = "https".equals(httpRequest.getScheme());
    try {
      boolean newToken = false;
      AuthenticationToken token;
      try {
        token = getToken(httpRequest);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Got token {} from httpRequest {}", token,
              getRequestURL(httpRequest));
        }
      } catch (AuthenticationException ex) {
        LOG.warn("AuthenticationToken ignored: " + ex.getMessage());
        if (!ex.getMessage().equals("Empty token")) {
          // will be sent back in a 401 unless filter authenticates
          authenticationEx = ex;
        }
        token = null;
      }
      if (managementOperation(token, httpRequest, httpResponse)) {
        if (token == null) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Request [{}] triggering authentication. handler: {}",
                getRequestURL(httpRequest), this.getClass());
          }
          token = authenticate(httpRequest, httpResponse);
          if (token != null && token != AuthenticationToken.ANONYMOUS) {
//            if (token.getMaxInactives() > 0) {
//              token.setMaxInactives(System.currentTimeMillis()
//                  + getTokenMaxInactiveInterval() * 1000);
//            }
            if (token.getExpires() != 0) {
              token.setExpires(System.currentTimeMillis()
                  + getTokenValidity() * 1000);
            }
          }
          newToken = true;
        }
        if (token != null) {
          unauthorizedResponse = false;
          if (LOG.isDebugEnabled()) {
            LOG.debug("Request [{}] user [{}] authenticated",
                getRequestURL(httpRequest), token.getUserName());
          }
          final AuthenticationToken authToken = token;
          httpRequest = new HttpServletRequestWrapper(httpRequest) {

            @Override
            public String getAuthType() {
              return authToken.getType();
            }

            @Override
            public String getRemoteUser() {
              return authToken.getUserName();
            }

            @Override
            public Principal getUserPrincipal() {
              return (authToken != AuthenticationToken.ANONYMOUS) ?
                  authToken : null;
            }
          };

          // If cookie persistence is configured to false,
          // it means the cookie will be a session cookie.
          // If the token is an old one, renew the its tokenMaxInactiveInterval.
          if (!newToken && !isCookiePersistent()
              && getTokenMaxInactiveInterval() > 0) {
//            token.setMaxInactives(System.currentTimeMillis()
//                + getTokenMaxInactiveInterval() * 1000);
            token.setExpires(token.getExpires());
            newToken = true;
          }
          if (newToken && !token.isExpired()
              && token != AuthenticationToken.ANONYMOUS) {
            String signedToken = signer.sign(token.toString());
            createAuthCookie(httpResponse, signedToken, getCookieDomain(),
                getCookiePath(), token.getExpires(),
                isCookiePersistent(), isHttps);
            KerberosToken kerberosToken = new KerberosToken(token.getUserName(), token.toString());
            SecurityUtils.getSubject().login(kerberosToken);
          }
          doFilter(filterChain, httpRequest, httpResponse);
        }
      } else {
        if (LOG.isDebugEnabled()) {
          LOG.debug("managementOperation returned false for request {}."
              + " token: {}", getRequestURL(httpRequest), token);
        }
        unauthorizedResponse = false;
      }
    } catch (AuthenticationException ex) {
      // exception from the filter itself is fatal
      errCode = HttpServletResponse.SC_FORBIDDEN;
      authenticationEx = ex;
      if (LOG.isDebugEnabled()) {
        LOG.debug("Authentication exception: " + ex.getMessage(), ex);
      } else {
        LOG.warn("Authentication exception: " + ex.getMessage());
      }
    }
    if (unauthorizedResponse) {
      if (!httpResponse.isCommitted()) {
        createAuthCookie(httpResponse, "", getCookieDomain(),
            getCookiePath(), 0, isCookiePersistent(), isHttps);
        // If response code is 401. Then WWW-Authenticate Header should be
        // present.. reset to 403 if not found..
        if ((errCode == HttpServletResponse.SC_UNAUTHORIZED)
            && (!httpResponse.containsHeader(KerberosAuthenticator.WWW_AUTHENTICATE))) {
          errCode = HttpServletResponse.SC_FORBIDDEN;
        }
        if (authenticationEx == null) {
          httpResponse.sendError(errCode, "Authentication required");
        } else {
          httpResponse.sendError(errCode, authenticationEx.getMessage());
        }
      }
    }
  }

  /**
   * It enforces the the Kerberos SPNEGO authentication sequence returning an
   * {@link AuthenticationToken} only after the Kerberos SPNEGO sequence has
   * completed successfully.
   *
   * @param request  the HTTP client request.
   * @param response the HTTP client response.
   * @return an authentication token if the Kerberos SPNEGO sequence is complete
   * and valid, <code>null</code> if it is in progress (in this case the handler
   * handles the response to the client).
   * @throws IOException             thrown if an IO error occurred.
   * @throws AuthenticationException thrown if Kerberos SPNEGO sequence failed.
   */
  public AuthenticationToken authenticate(HttpServletRequest request,
                                          final HttpServletResponse response)
      throws IOException, AuthenticationException {
    AuthenticationToken token = null;
    String authorization = request.getHeader(
        KerberosAuthenticator.AUTHORIZATION);

    if (authorization == null
        || !authorization.startsWith(KerberosAuthenticator.NEGOTIATE)) {
      response.setHeader(KerberosAuthenticator.WWW_AUTHENTICATE, KerberosAuthenticator.NEGOTIATE);
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      if (authorization == null) {
        LOG.trace("SPNEGO starting for url: {}", request.getRequestURL());
      } else {
        LOG.warn("'" + KerberosAuthenticator.AUTHORIZATION +
            "' does not start with '" +
            KerberosAuthenticator.NEGOTIATE + "' :  {}", authorization);
      }
    } else {
      authorization = authorization.substring(
          KerberosAuthenticator.NEGOTIATE.length()).trim();
      final Base64 base64 = new Base64(0);
      final byte[] clientToken = base64.decode(authorization);
      try {
        final String serverPrincipal =
            KerberosUtil.getTokenServerName(clientToken);
        if (!serverPrincipal.startsWith("HTTP/")) {
          throw new IllegalArgumentException(
              "Invalid server principal " + serverPrincipal +
                  "decoded from client request");
        }
        token = Subject.doAs(serverSubject,
            new PrivilegedExceptionAction<AuthenticationToken>() {
              @Override
              public AuthenticationToken run() throws Exception {
                return runWithPrincipal(serverPrincipal, clientToken,
                    base64, response);
              }
            });
      } catch (PrivilegedActionException ex) {
        if (ex.getException() instanceof IOException) {
          throw (IOException) ex.getException();
        } else {
          throw new AuthenticationException(ex.getException());
        }
      } catch (Exception ex) {
        throw new AuthenticationException(ex);
      }
    }
    return token;
  }

  private AuthenticationToken runWithPrincipal(String serverPrincipal,
                                               byte[] clientToken, Base64 base64,
                                               HttpServletResponse response)
      throws IOException, GSSException {
    GSSContext gssContext = null;
    GSSCredential gssCreds = null;
    AuthenticationToken token = null;
    try {
      LOG.trace("SPNEGO initiated with server principal [{}]", serverPrincipal);
      gssCreds = this.gssManager.createCredential(
          this.gssManager.createName(serverPrincipal,
              KerberosUtil.NT_GSS_KRB5_PRINCIPAL_OID),
          GSSCredential.INDEFINITE_LIFETIME,
          new Oid[]{KerberosUtil.GSS_SPNEGO_MECH_OID, KerberosUtil.GSS_KRB5_MECH_OID},
          GSSCredential.ACCEPT_ONLY);
      gssContext = this.gssManager.createContext(gssCreds);
      byte[] serverToken = gssContext.acceptSecContext(clientToken, 0,
          clientToken.length);
      if (serverToken != null && serverToken.length > 0) {
        String authenticate = base64.encodeToString(serverToken);
        response.setHeader(KerberosAuthenticator.WWW_AUTHENTICATE,
            KerberosAuthenticator.NEGOTIATE + " " +
                authenticate);
      }
      if (!gssContext.isEstablished()) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        LOG.trace("SPNEGO in progress");
      } else {
        String clientPrincipal = gssContext.getSrcName().toString();
        KerberosName kerberosName = new KerberosName(clientPrincipal);
        String userName = kerberosName.getShortName();
        token = new AuthenticationToken(userName, clientPrincipal, getType());
        response.setStatus(HttpServletResponse.SC_OK);
        LOG.trace("SPNEGO completed for client principal [{}]",
            clientPrincipal);
      }
    } finally {
      if (gssContext != null) {
        gssContext.dispose();
      }
      if (gssCreds != null) {
        gssCreds.dispose();
      }
    }
    return token;
  }

  /**
   * Returns the full URL of the request including the query string.
   * <p>
   * Used as a convenience method for logging purposes.
   *
   * @param request the request object.
   * @return the full URL of the request including the query string.
   */
  protected String getRequestURL(HttpServletRequest request) {
    StringBuffer sb = request.getRequestURL();
    if (request.getQueryString() != null) {
      sb.append("?").append(request.getQueryString());
    }
    return sb.toString();
  }

  /**
   * Returns the {@link AuthenticationToken} for the request.
   * <p>
   * It looks at the received HTTP cookies and extracts the value of the
   * {@link AuthenticatedURL#AUTH_COOKIE}
   * if present. It verifies the signature and if correct it creates the
   * {@link AuthenticationToken} and returns
   * it.
   * <p>
   * If this method returns <code>null</code> the filter will invoke the configured
   * {@link AuthenticationHandler}
   * to perform user authentication.
   *
   * @param request request object.
   * @return the Authentication token if the request is authenticated, <code>null</code> otherwise.
   * @throws IOException             thrown if an IO error occurred.
   * @throws AuthenticationException thrown if the token is invalid or if it has expired.
   */
  protected AuthenticationToken getToken(HttpServletRequest request)
      throws AuthenticationException {
    AuthenticationToken token;
    Cookie[] cookies = request.getCookies();
    token = getTokenFromCookies(cookies);
    return token;
  }

  public static AuthenticationToken getTokenFromCookies(Cookie[] cookies)
      throws AuthenticationException {
    AuthenticationToken token = null;
    String tokenStr = null;
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(AuthenticatedURL.AUTH_COOKIE)) {
          tokenStr = cookie.getValue();
          if (tokenStr.isEmpty()) {
            throw new AuthenticationException("Empty token");
          }
          try {
            tokenStr = signer.verifyAndExtract(tokenStr);
          } catch (SignerException ex) {
            throw new AuthenticationException(ex);
          }
          break;
        }
      }
    }
    if (tokenStr != null) {
      token = AuthenticationToken.parse(tokenStr);
      boolean match = verifyTokenType(token);
      if (!match) {
        throw new AuthenticationException("Invalid AuthenticationToken type");
      }
      if (token.isExpired()) {
        throw new AuthenticationException("AuthenticationToken expired");
      }
    }
    return token;
  }

  public static KerberosToken getKerberosTokenFromCookies(
      Map<String, javax.ws.rs.core.Cookie> cookies)
      throws org.apache.shiro.authc.AuthenticationException {
    KerberosToken kerberosToken = null;
    String tokenStr = null;
    if (cookies != null) {
      for (javax.ws.rs.core.Cookie cookie : cookies.values()) {
        if (cookie.getName().equals(KerberosAuthenticator.AUTHORIZATION)) {
          tokenStr = cookie.getValue();
          if (tokenStr.isEmpty()) {
            throw new org.apache.shiro.authc.AuthenticationException("Empty token");
          }
          try {
            tokenStr = tokenStr.substring(KerberosAuthenticator.NEGOTIATE.length()).trim();
          } catch (Exception ex) {
            throw new org.apache.shiro.authc.AuthenticationException(ex);
          }
          break;
        }
      }
    }
    if (tokenStr != null) {
      try {
        AuthenticationToken authToken = AuthenticationToken.parse(tokenStr);
        boolean match = verifyTokenType(authToken);
        if (!match) {
          throw new
              org.apache.shiro.authc.AuthenticationException("Invalid AuthenticationToken type");
        }
        if (authToken.isExpired()) {
          throw new org.apache.shiro.authc.AuthenticationException("AuthenticationToken expired");
        }
        kerberosToken = new KerberosToken(authToken.getUserName(), tokenStr);
      } catch (AuthenticationException ex) {
        throw new org.apache.shiro.authc.AuthenticationException(ex);
      }
    }
    return kerberosToken;
  }

  /**
   * This method verifies if the specified token type matches one of the the
   * token types supported by our Authentication provider : {@link KerberosRealm}
   *
   * @param token The token whose type needs to be verified.
   * @return true   If the token type matches one of the supported token types
   * false  Otherwise
   */
  protected static boolean verifyTokenType(AuthenticationToken token) {
    return getType().equals(token.getType());
  }

  /**
   * Delegates call to the servlet filter chain. Sub-classes my override this
   * method to perform pre and post tasks.
   *
   * @param filterChain the filter chain object.
   * @param request     the request object.
   * @param response    the response object.
   * @throws IOException      thrown if an IO error occurred.
   * @throws ServletException thrown if a processing error occurred.
   */
  protected void doFilter(FilterChain filterChain, HttpServletRequest request,
                          HttpServletResponse response) throws IOException, ServletException {
    filterChain.doFilter(request, response);
  }

  /**
   * Creates the Hadoop authentication HTTP cookie.
   *
   * @param resp               the response object.
   * @param token              authentication token for the cookie.
   * @param domain             the cookie domain.
   * @param path               the cookie path.
   * @param expires            UNIX timestamp that indicates the expire date of the
   *                           cookie. It has no effect if its value &lt; 0.
   * @param isSecure           is the cookie secure?
   * @param isCookiePersistent whether the cookie is persistent or not.
   *                           <p>
   *                           XXX the following code duplicate some logic in Jetty / Servlet API,
   *                           because of the fact that Hadoop is stuck at servlet 2.5 and jetty 6
   *                           right now.
   */
  public static void createAuthCookie(HttpServletResponse resp, String token,
                                      String domain, String path, long expires,
                                      boolean isCookiePersistent,
                                      boolean isSecure) {
    StringBuilder sb = new StringBuilder(AuthenticatedURL.AUTH_COOKIE)
        .append("=");
    if (token != null && token.length() > 0) {
      sb.append("\"").append(token).append("\"");
    }

    if (path != null) {
      sb.append("; Path=").append(path);
    }

    if (domain != null) {
      sb.append("; Domain=").append(domain);
    }

    if (expires >= 0 && isCookiePersistent) {
      Date date = new Date(expires);
      SimpleDateFormat df = new SimpleDateFormat("EEE, " +
          "dd-MMM-yyyy HH:mm:ss zzz");
      df.setTimeZone(TimeZone.getTimeZone("GMT"));
      sb.append("; Expires=").append(df.format(date));
    }

    if (isSecure) {
      sb.append("; Secure");
    }

    sb.append("; HttpOnly");
    resp.addHeader("Set-Cookie", sb.toString());
  }

  protected Properties getConfiguration() {
    Properties props = new Properties();
    props.put(COOKIE_DOMAIN, cookieDomain);
    props.put(COOKIE_PATH, cookiePath);
    props.put(COOKIE_PERSISTENT, isCookiePersistent);
    props.put(SIGNER_SECRET_PROVIDER, "file");
    props.put(SIGNATURE_SECRET_FILE, signatureSecretFile);
    props.put(AUTH_TYPE, type);
    props.put(AUTH_TOKEN_VALIDITY, tokenValidity);
    props.put(AUTH_TOKEN_MAX_INACTIVE_INTERVAL, tokenMaxInactiveInterval);
    props.put(PRINCIPAL, principal);
    props.put(KEYTAB, keytab);
    props.put(NAME_RULES, nameRules);
    return props;
  }

  /**
   * Returns the max inactive interval time of the generated tokens.
   *
   * @return the max inactive interval time of the generated tokens in seconds.
   */
  protected long getTokenMaxInactiveInterval() {
    return tokenMaxInactiveInterval / 1000;
  }

  /**
   * Returns the tokenValidity time of the generated tokens.
   *
   * @return the tokenValidity time of the generated tokens, in seconds.
   */
  protected long getTokenValidity() {
    return tokenValidity / 1000;
  }

  /**
   * Returns the cookie domain to use for the HTTP cookie.
   *
   * @return the cookie domain to use for the HTTP cookie.
   */
  protected String getCookieDomain() {
    return cookieDomain;
  }

  /**
   * Returns the cookie path to use for the HTTP cookie.
   *
   * @return the cookie path to use for the HTTP cookie.
   */
  protected String getCookiePath() {
    return cookiePath;
  }

  /**
   * Returns the cookie persistence to use for the HTTP cookie.
   *
   * @return the cookie persistence to use for the HTTP cookie.
   */
  public boolean isCookiePersistent() {
    return isCookiePersistent;
  }

  public void setTokenMaxInactiveInterval(long tokenMaxInactiveInterval) {
    this.tokenMaxInactiveInterval = tokenMaxInactiveInterval * 1000;
  }

  public void setTokenValidity(long tokenValidity) {
    this.tokenValidity = tokenValidity * 1000;
  }

  public void setCookieDomain(String cookieDomain) {
    this.cookieDomain = cookieDomain;
  }

  public void setCookiePath(String cookiePath) {
    this.cookiePath = cookiePath;
  }

  public void setCookiePersistent(boolean cookiePersistent) {
    isCookiePersistent = cookiePersistent;
  }

  public String getPrincipal() {
    return principal;
  }

  public void setPrincipal(String principal) {
    this.principal = principal;
  }

  public void setKeytab(String keytab) {
    this.keytab = keytab;
  }

  public String getNameRules() {
    return nameRules;
  }

  public void setNameRules(String nameRules) {
    this.nameRules = nameRules;
  }

  public String getSignatureSecretFile() {
    return signatureSecretFile;
  }

  public void setSignatureSecretFile(String signatureSecretFile) {
    this.signatureSecretFile = signatureSecretFile;
  }

  /**
   * Releases any resources initialized by the authentication handler.
   * <p>
   * It destroys the Kerberos context.
   */
  public void destroy() {
    keytab = null;
    serverSubject = null;

    if (secretProvider != null && destroySecretProvider) {
      secretProvider.destroy();
      secretProvider = null;
    }
  }

  /**
   * Returns the authentication type of the authentication handler, 'kerberos'.
   * <p>
   *
   * @return the authentication type of the authentication handler, 'kerberos'.
   */
  public static String getType() {
    return type;
  }

  /**
   * Returns the Kerberos principals used by the authentication handler.
   *
   * @return the Kerberos principals used by the authentication handler.
   */
  protected Set<KerberosPrincipal> getPrincipals() {
    return serverSubject.getPrincipals(KerberosPrincipal.class);
  }

  /**
   * Returns the keytab used by the authentication handler.
   *
   * @return the keytab used by the authentication handler.
   */
  protected String getKeytab() {
    return keytab;
  }
}
