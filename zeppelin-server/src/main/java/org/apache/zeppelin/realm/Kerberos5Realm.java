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

package org.apache.zeppelin.realm;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.sun.security.auth.login.ConfigFile;
import com.sun.security.auth.module.Krb5LoginModule;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedAction;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Authentication-only realm for Kerberos V5.
 */
public class Kerberos5Realm implements Realm {

  private static final Logger LOGGER = LoggerFactory.getLogger(Kerberos5Realm.class);

  private static final Splitter AT_SPLITTER = Splitter.on("@");
  /**
   * Standard Object Identifier for the Kerberos 5 GSS-API mechanism.
   */
  private static final String GSS_KRB5_MECH_OID = "1.2.840.113554.1.2.2";

  /**
   * Standard Object Identifier for the SPNEGO GSS-API mechanism.
   */
  private static final String GSS_SPNEGO_MECH_OID = "1.3.6.1.5.5.2";

  private static final String JAAS_CONF_TEMPLATE =
      "%s {\n"
          + Krb5LoginModule.class.getName()
          + " required useKeyTab=true storeKey=true doNotPrompt=true isInitiator=false "
          + "keyTab=\"%s\" principal=\"%s\" debug=%s;\n"
          + "};";

  private File serverKeyTab;
  private KerberosPrincipal serverPrincipal;

  @Parameters(separators = "=")
  public static class Options {
    private static final String SERVER_KEYTAB_ARGNAME = "-kerberos_server_keytab";
    private static final String SERVER_PRINCIPAL_ARGNAME = "-kerberos_server_principal";

    @Parameter(names = SERVER_KEYTAB_ARGNAME, description = "Path to the server keytab.")
    public File serverKeytab;

    @Parameter(names = SERVER_PRINCIPAL_ARGNAME,
        description = "Kerberos server principal to use, usually of the form "
            + "HTTP/aurora.example.com@EXAMPLE.COM")
    public KerberosPrincipal serverPrincipal;

    @Parameter(names = "-kerberos_debug",
        description = "Produce additional Kerberos debugging output.",
        arity = 1)
    public boolean kerberosDebug = false;
  }

  private GSSManager gssManager;
  private GSSCredential serverCredential;

  public Kerberos5Realm() {
    this.gssManager = GSSManager.getInstance();
    this.serverKeyTab = new File(ZeppelinConfiguration.create().getString(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_SPNEGO_KEYTAB));
    this.serverPrincipal = new KerberosPrincipal(ZeppelinConfiguration.create().getString(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_SPNEGO_PRINCIPAL));
    // TODO(ksweeney): Find a better way to configure JAAS in code.
    String jaasConf = String.format(
        JAAS_CONF_TEMPLATE,
        getClass().getName(),
        serverKeyTab.getAbsolutePath(),
        serverPrincipal.getName(),
        true);
    LOGGER.debug("Generated jaas.conf: " + jaasConf);

    File jaasConfFile;
    try {
      jaasConfFile = File.createTempFile("jaas", "conf");
      jaasConfFile.deleteOnExit();
      Files.asCharSink(jaasConfFile, StandardCharsets.UTF_8).write(jaasConf);
    } catch (IOException e) {
      LOGGER.error("Fail to generate jaas.conf", e);
      return;
    }

    try {
      LoginContext loginContext = new LoginContext(
          getClass().getName(),
          null /* subject (read from jaas config file passed below) */,
          null /* callbackHandler */,
          new ConfigFile(jaasConfFile.toURI()));
      loginContext.login();
      this.serverCredential = Subject.doAs(
          loginContext.getSubject(),
          (PrivilegedAction<GSSCredential>) () -> {
            try {
              return gssManager.createCredential(
                  null /* Use the service principal name defined in jaas.conf */,
                  GSSCredential.INDEFINITE_LIFETIME,
                  new Oid[] {new Oid(GSS_SPNEGO_MECH_OID), new Oid(GSS_KRB5_MECH_OID)},
                  GSSCredential.ACCEPT_ONLY);
            } catch (GSSException e) {
              throw new RuntimeException(e);
            }
          });
    } catch (LoginException e) {
      throw new RuntimeException(e);
    }
  }

  @Inject
  Kerberos5Realm(GSSManager gssManager, GSSCredential serverCredential) {
    this.gssManager = requireNonNull(gssManager);
    this.serverCredential = requireNonNull(serverCredential);
  }

  @Override
  public String getName() {
    return getClass().getName();
  }

  @Override
  public boolean supports(AuthenticationToken token) {
    return token instanceof AuthorizeHeaderToken;
  }

  @Override
  public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {

    byte[] tokenFromInitiator = ((AuthorizeHeaderToken) token).getAuthorizeHeaderValue();
    GSSContext context;
    try {
      context = gssManager.createContext(serverCredential);
      context.acceptSecContext(tokenFromInitiator, 0, tokenFromInitiator.length);
    } catch (GSSException e) {
      e.printStackTrace();
      throw new AuthenticationException(e);
    }

    // Technically the GSS-API requires us to continue sending data back and forth in a loop
    // until the context is established, but we can short-circuit here since we know we're using
    // Kerberos V5 directly or Kerberos V5-backed SPNEGO. This is important because it means we
    // don't need to keep state between requests.
    // From http://docs.oracle.com/javase/7/docs/technotes/guides/security/jgss/single-signon.html
    // "In the case of the Kerberos V5 mechanism, there is no more than one round trip of
    // tokens during context establishment."
    LOGGER.info("context.isEstablished: " + context.isEstablished());
    if (context.isEstablished()) {
      try {
        KerberosPrincipal kerberosPrincipal =
            new KerberosPrincipal(context.getSrcName().toString());
        SimpleAuthenticationInfo authenticationInfo = new SimpleAuthenticationInfo(
            new SimplePrincipalCollection(
                ImmutableList.of(
                    // We assume there's a single Kerberos realm in use here. Most Authorizer
                    // implementations care about the "simple" username instead of the full
                    // principal.
                    AT_SPLITTER.splitToList(kerberosPrincipal.getName()).get(0),
                    kerberosPrincipal),
                getName()),
            null /* There are no credentials that can be cached. */);
        LOGGER.debug("SPNEGO auth successfully");
        return authenticationInfo;
      } catch (GSSException | IndexOutOfBoundsException e) {
        e.printStackTrace();
        LOGGER.error("Fail to auth", e);
        throw new AuthenticationException(e);
      }
    } else {
      LOGGER.error("GSSContext was not established with a single message.");
      throw new AuthenticationException("GSSContext was not established with a single message.");
    }
  }
}