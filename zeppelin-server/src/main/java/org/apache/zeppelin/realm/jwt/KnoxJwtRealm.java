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
package org.apache.zeppelin.realm.jwt;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletException;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.Groups;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created for org.apache.zeppelin.server
 */
public class KnoxJwtRealm extends AuthorizingRealm {

  private static final Logger LOGGER = LoggerFactory.getLogger(KnoxJwtRealm.class);

  private String providerUrl;
  private String redirectParam;
  private String cookieName;
  private String publicKeyPath;
  private String login;
  private String logout;

  private String principalMapping;
  private String groupPrincipalMapping;

  private SimplePrincipalMapper mapper = new SimplePrincipalMapper();
  /**
   * Configuration object needed by for hadoop classes
   */
  private Configuration hadoopConfig;

  /**
   * Hadoop Groups implementation.
   */
  private Groups hadoopGroups;

  @Override
  protected void onInit() {
    super.onInit();
    if (principalMapping != null && !principalMapping.isEmpty()
        || groupPrincipalMapping != null && !groupPrincipalMapping.isEmpty()) {
      try {
        mapper.loadMappingTable(principalMapping, groupPrincipalMapping);
      } catch (PrincipalMappingException e) {
        LOGGER.error("PrincipalMappingException in onInit", e);
      }
    }

    try {
      hadoopConfig = new Configuration();
      hadoopGroups = new Groups(hadoopConfig);
    } catch (final Exception e) {
      LOGGER.error("Exception in onInit", e);
    }

  }

  @Override
  public boolean supports(AuthenticationToken token) {
    return token != null && token instanceof JWTAuthenticationToken;
  }


  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
    JWTAuthenticationToken upToken = (JWTAuthenticationToken) token;

    if (validateToken(upToken.getToken())) {
      try {
        SimpleAccount account = new SimpleAccount(getName(upToken), upToken.getToken(), getName());
        account.addRole(mapGroupPrincipals(getName(upToken)));
        return account;
      } catch (ParseException e) {
        LOGGER.error("ParseException in doGetAuthenticationInfo", e);
      }
    }
    return null;
  }

  private String getName(JWTAuthenticationToken upToken) throws ParseException {
    SignedJWT signed = SignedJWT.parse(upToken.getToken());
    String userName = signed.getJWTClaimsSet().getSubject();
    return userName;
  }

  protected boolean validateToken(String token) {
    try {
      SignedJWT signed = SignedJWT.parse(token);
      return validateSignature(signed);
    } catch (ParseException ex) {
      LOGGER.info("ParseException in validateToken", ex);
      return false;
    }
  }

  public static RSAPublicKey parseRSAPublicKey(String pem)
      throws IOException, ServletException {
    String PEM_HEADER = "-----BEGIN CERTIFICATE-----\n";
    String PEM_FOOTER = "\n-----END CERTIFICATE-----";
    String fullPem = PEM_HEADER + pem + PEM_FOOTER;
    PublicKey key = null;
    try {
      CertificateFactory fact = CertificateFactory.getInstance("X.509");
      ByteArrayInputStream is = new ByteArrayInputStream(
          FileUtils.readFileToString(new File(pem)).getBytes("UTF8"));
      X509Certificate cer = (X509Certificate) fact.generateCertificate(is);
      key = cer.getPublicKey();
    } catch (CertificateException ce) {
      String message = null;
      if (pem.startsWith(PEM_HEADER)) {
        message = "CertificateException - be sure not to include PEM header "
            + "and footer in the PEM configuration element.";
      } else {
        message = "CertificateException - PEM may be corrupt";
      }
      throw new ServletException(message, ce);
    } catch (UnsupportedEncodingException uee) {
      throw new ServletException(uee);
    } catch (IOException e) {
      throw new IOException(e);
    }
    return (RSAPublicKey) key;
  }

  protected boolean validateSignature(SignedJWT jwtToken) {
    boolean valid = false;
    if (JWSObject.State.SIGNED == jwtToken.getState()) {

      if (jwtToken.getSignature() != null) {

        try {
          RSAPublicKey publicKey = parseRSAPublicKey(publicKeyPath);
          JWSVerifier verifier = new RSASSAVerifier(publicKey);
          if (verifier != null && jwtToken.verify(verifier)) {
            valid = true;
          }
        } catch (Exception e) {
          LOGGER.info("Exception in validateSignature", e);
        }
      }
    }
    return valid;
  }

  @Override
  protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    Set<String> roles = mapGroupPrincipals(principals.toString());
    return new SimpleAuthorizationInfo(roles);
  }

  /**
   * Query the Hadoop implementation of {@link Groups} to retrieve groups for
   * provided user.
   */
  public Set<String> mapGroupPrincipals(final String mappedPrincipalName) {
    /* return the groups as seen by Hadoop */
    Set<String> groups = null;
    try {
      hadoopGroups.refresh();
      final List<String> groupList = hadoopGroups
          .getGroups(mappedPrincipalName);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(String.format("group found %s, %s",
            mappedPrincipalName, groupList.toString()));
      }

      groups = new HashSet<>(groupList);

    } catch (final IOException e) {
      if (e.toString().contains("No groups found for user")) {
        /* no groups found move on */
        LOGGER.info(String.format("No groups found for user %s", mappedPrincipalName));

      } else {
        /* Log the error and return empty group */
        LOGGER.info(String.format("errorGettingUserGroups for %s", mappedPrincipalName));
      }
      groups = new HashSet();
    }
    return groups;
  }

  public String getProviderUrl() {
    return providerUrl;
  }

  public void setProviderUrl(String providerUrl) {
    this.providerUrl = providerUrl;
  }

  public String getRedirectParam() {
    return redirectParam;
  }

  public void setRedirectParam(String redirectParam) {
    this.redirectParam = redirectParam;
  }

  public String getCookieName() {
    return cookieName;
  }

  public void setCookieName(String cookieName) {
    this.cookieName = cookieName;
  }

  public String getPublicKeyPath() {
    return publicKeyPath;
  }

  public void setPublicKeyPath(String publicKeyPath) {
    this.publicKeyPath = publicKeyPath;
  }

  public String getLogin() {
    return login;
  }

  public void setLogin(String login) {
    this.login = login;
  }

  public String getLogout() {
    return logout;
  }

  public void setLogout(String logout) {
    this.logout = logout;
  }

  public String getPrincipalMapping() {
    return principalMapping;
  }

  public void setPrincipalMapping(String principalMapping) {
    this.principalMapping = principalMapping;
  }

  public String getGroupPrincipalMapping() {
    return groupPrincipalMapping;
  }

  public void setGroupPrincipalMapping(String groupPrincipalMapping) {
    this.groupPrincipalMapping = groupPrincipalMapping;
  }
}

