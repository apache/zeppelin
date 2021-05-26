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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.shiro.authc.AccountException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.zeppelin.common.JsonSerializable;
import org.apache.zeppelin.notebook.repo.zeppelinhub.model.UserSessionContainer;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.utils.ZeppelinhubUtils;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.socket.NotebookServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@code Realm} implementation that uses the ZeppelinHub to authenticate users.
 *
 */
public class ZeppelinHubRealm extends AuthorizingRealm {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinHubRealm.class);
  private static final String DEFAULT_ZEPPELINHUB_URL = "https://www.zeppelinhub.com";
  private static final String USER_LOGIN_API_ENDPOINT = "api/v1/users/login";
  private static final String USER_SESSION_HEADER = "X-session";
  private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

  private final CloseableHttpClient httpClient;

  private String zeppelinhubUrl;
  private String name;

  public ZeppelinHubRealm() {
    super();
    LOG.debug("Init ZeppelinhubRealm");
    //TODO(anthonyc): think about more setting for this HTTP client.
    //                eg: if user uses proxy etcetc...
    httpClient = HttpClients.createDefault();
    name = getClass().getName() + "_" + INSTANCE_COUNT.getAndIncrement();
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authToken)
          throws AuthenticationException {
    UsernamePasswordToken token = (UsernamePasswordToken) authToken;
    if (StringUtils.isBlank(token.getUsername())) {
      throw new AccountException("Empty usernames are not allowed by this realm.");
    }
    String loginPayload = createLoginPayload(token.getUsername(), token.getPassword());
    User user = authenticateUser(loginPayload);
    LOG.debug("{} successfully login via ZeppelinHub", user.login);
    return new SimpleAuthenticationInfo(user.login, token.getPassword(), name);
  }

  @Override
  protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    // TODO(xxx): future work will be done here.
    return null;
  }

  @Override
  protected void onInit() {
    super.onInit();
  }

  /**
   * Setter of ZeppelinHub URL, this will be called by Shiro based on zeppelinhubUrl property
   * in shiro.ini file.
   *
   * It will also perform a check of ZeppelinHub url {@link #isZeppelinHubUrlValid},
   * if the url is not valid, the default zeppelinhub url will be used.
   *
   * @param url
   */
  public void setZeppelinhubUrl(String url) {
    if (StringUtils.isBlank(url)) {
      LOG.warn("Zeppelinhub url is empty, setting up default url {}", DEFAULT_ZEPPELINHUB_URL);
      zeppelinhubUrl = DEFAULT_ZEPPELINHUB_URL;
    } else {
      zeppelinhubUrl = (isZeppelinHubUrlValid(url) ? url : DEFAULT_ZEPPELINHUB_URL);
      LOG.info("Setting up Zeppelinhub url to {}", zeppelinhubUrl);
    }
  }

  /**
   * Send to ZeppelinHub a login request based on the request body which is a JSON that contains 2
   * fields "login" and "password".
   *
   * @param requestBody JSON string of ZeppelinHub payload.
   * @return Account object with login, name (if set in ZeppelinHub), and mail.
   * @throws AuthenticationException if fail to login.
   */
  protected User authenticateUser(String requestBody) {
    String responseBody;
    String userSession;
    HttpPut put = new HttpPut(String.join("/", zeppelinhubUrl, USER_LOGIN_API_ENDPOINT));
    put.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
    try (CloseableHttpResponse response = httpClient.execute(put)){
      if (HttpStatus.SC_OK != response.getStatusLine().getStatusCode()) {
        LOG.error("Cannot login user, HTTP status code is {} instead on 200 (OK)", response.getStatusLine().getStatusCode());
        put.releaseConnection();
        throw new AuthenticationException("Couldnt login to ZeppelinHub. "
            + "Login or password incorrect");
      }
      responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      userSession = response.getFirstHeader(USER_SESSION_HEADER).getValue();
      put.releaseConnection();
    } catch (IOException e) {
      throw new AuthenticationException("Cannot login user", e);
    }

    User account;
    try {
      account = User.fromJson(responseBody);
    } catch (JsonParseException e) {
      throw new AuthenticationException("Cannot login to ZeppelinHub", e);
    }

    onLoginSuccess(account.login, userSession);

    return account;
  }

  /**
   * Create a JSON String that represent login payload.
   *
   * Payload will look like:
   * {@code
   *  {
   *   'login': 'userLogin',
   *   'password': 'userpassword'
   *  }
   * }
   * @param login
   * @param pwd
   * @return
   */
  protected String createLoginPayload(String login, char[] pwd) {
    StringBuilder sb = new StringBuilder("{\"login\":\"");
    return sb.append(login).append("\", \"password\":\"").append(pwd).append("\"}").toString();
  }

  /**
   * Perform a Simple URL check by using {@code URI(url).toURL()}.
   * If the url is not valid, the try-catch condition will catch the exceptions and return false,
   * otherwise true will be returned.
   *
   * @param url
   * @return
   */
  protected boolean isZeppelinHubUrlValid(String url) {
    boolean valid;
    try {
      new URI(url).toURL();
      valid = true;
    } catch (URISyntaxException | MalformedURLException e) {
      LOG.error("Zeppelinhub url is not valid, default ZeppelinHub url will be used.", e);
      valid = false;
    }
    return valid;
  }

  /**
   * Helper class that will be use to fromJson ZeppelinHub response.
   */
  protected static class User implements JsonSerializable {
    private static final Gson gson = new Gson();
    public String login;
    public String email;
    public String name;

    @Override
    public String toJson() {
      return gson.toJson(this);
    }

    public static User fromJson(String json) {
      return gson.fromJson(json, User.class);
    }
  }

  public void onLoginSuccess(String username, String session) {
    UserSessionContainer.instance.setSession(username, session);

    /* TODO(xxx): add proper roles */
    HashSet<String> userAndRoles = new HashSet<>();
    userAndRoles.add(username);
    ServiceContext context = new ServiceContext(
        new org.apache.zeppelin.user.AuthenticationInfo(username), userAndRoles);
    try {
      // This can failed to get NotebookServer instance with very rare cases
      NotebookServer.getInstance().broadcastReloadedNoteList(context);
    } catch (IOException e) {
      LOG.error("Fail to broadcastReloadedNoteList", e);
    }

    ZeppelinhubUtils.userLoginRoutine(username);
  }

  @Override
  public void onLogout(PrincipalCollection principals) {
    ZeppelinhubUtils.userLogoutRoutine((String) principals.getPrimaryPrincipal());
  }
}
