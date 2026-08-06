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
package org.apache.zeppelin.rest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.ticket.TicketContainer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();
  private static MiniZeppelinServer zepServer;

  @BeforeAll
  static void init() throws Exception {
    zepServer = new MiniZeppelinServer(SecurityRestApiTest.class.getSimpleName());
    zepServer.addConfigFile("shiro.ini", ZEPPELIN_SHIRO);
    zepServer.start();
  }

  @AfterAll
  static void destroy() throws Exception {
    zepServer.destroy();
  }

  @BeforeEach
  void setup() {
    zConf = zepServer.getZeppelinConfiguration();
  }

  @Test
  void testTicket() throws IOException {
    CloseableHttpResponse get = httpGet("/security/ticket", "admin", "password1");
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
        new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> body = (Map<String, String>) resp.get("body");
    assertThat("Paramater principal", body.get("principal"),
        CoreMatchers.equalTo("admin"));
    assertThat("Paramater ticket", body.get("ticket"),
        CoreMatchers.not("anonymous"));
    get.close();
  }

  @Test
  void testLoginTicketIsNotLogged() throws IOException {
    String principal = "user1";
    TicketContainer.instance.removeTicket(principal);
    TestAppender appender = new TestAppender();
    Logger logger = Logger.getLogger(LoginRestApi.class);
    Level previousLevel = logger.getLevel();
    boolean previousAdditivity = logger.getAdditivity();
    logger.setLevel(Level.TRACE);
    logger.setAdditivity(false);
    logger.addAppender(appender);

    try {
      HttpPost login = new HttpPost(getUrlToTest(zConf) + "/login");
      login.addHeader("Origin", getUrlToTest(zConf));
      List<NameValuePair> parameters = new ArrayList<>();
      parameters.add(new BasicNameValuePair("password", "password2"));
      parameters.add(new BasicNameValuePair("userName", principal));
      login.setEntity(new UrlEncodedFormEntity(parameters, StandardCharsets.UTF_8));

      try (CloseableHttpResponse post = getHttpClient().execute(login)) {
        Map<String, Object> resp = gson.fromJson(
            EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>(){}.getType());
        Map<String, String> body = (Map<String, String>) resp.get("body");
        String ticket = body.get("ticket");
        assertThat("Login response ticket", ticket, CoreMatchers.notNullValue());
        assertThat("Login response ticket", ticket, CoreMatchers.not("anonymous"));
        assertTrue(appender.contains("principal=" + principal));
        assertTrue(appender.contains("success=true"));
        assertFalse(appender.contains(ticket), "Login logs must not contain the ticket");
      }
    } finally {
      logger.removeAppender(appender);
      logger.setLevel(previousLevel);
      logger.setAdditivity(previousAdditivity);
      appender.close();
      TicketContainer.instance.removeTicket(principal);
    }
  }

  @Test
  void testSecurityTicketIsNotLogged() throws IOException {
    String principal = "user2";
    TicketContainer.instance.removeTicket(principal);
    TestAppender appender = new TestAppender();
    Logger logger = Logger.getLogger(SecurityRestApi.class);
    Level previousLevel = logger.getLevel();
    boolean previousAdditivity = logger.getAdditivity();
    logger.setLevel(Level.TRACE);
    logger.setAdditivity(false);
    logger.addAppender(appender);

    try (CloseableHttpResponse get =
             httpGet("/security/ticket", principal, "password3")) {
      Map<String, Object> resp = gson.fromJson(
          EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
          new TypeToken<Map<String, Object>>(){}.getType());
      Map<String, String> body = (Map<String, String>) resp.get("body");
      String ticket = body.get("ticket");
      assertThat("Security response ticket", ticket, CoreMatchers.notNullValue());
      assertThat("Security response ticket", ticket, CoreMatchers.not("anonymous"));
      assertTrue(appender.contains("principal=" + principal));
      assertTrue(appender.contains("success=true"));
      assertFalse(appender.contains(ticket), "Security ticket logs must not contain the ticket");
    } finally {
      logger.removeAppender(appender);
      logger.setLevel(previousLevel);
      logger.setAdditivity(previousAdditivity);
      appender.close();
      TicketContainer.instance.removeTicket(principal);
    }
  }

  @Test
  void testGetUserList() throws IOException {
    CloseableHttpResponse get = httpGet("/security/userlist/admi", "admin", "password1");
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
        new TypeToken<Map<String, Object>>(){}.getType());
    List<String> userList = (List) ((Map) resp.get("body")).get("users");
    assertThat("Search result size", userList.size(),
        CoreMatchers.equalTo(1));
    assertThat("Search result contains admin", userList.contains("admin"),
        CoreMatchers.equalTo(true));
    get.close();

    CloseableHttpResponse notUser = httpGet("/security/userlist/randomString", "admin", "password1");
    Map<String, Object> notUserResp = gson.fromJson(EntityUtils.toString(notUser.getEntity(), StandardCharsets.UTF_8),
        new TypeToken<Map<String, Object>>(){}.getType());
    List<String> emptyUserList = (List) ((Map) notUserResp.get("body")).get("users");
    assertThat("Search result size", emptyUserList.size(),
        CoreMatchers.equalTo(0));

    notUser.close();
  }

  @Test
  void testGetUserListWithRegexMetacharacters() throws IOException {
    // The search text is not a regular expression. Metacharacters must not break the endpoint.
    for (String searchText : new String[] {"%2A", "%28", "%2B"}) {
      CloseableHttpResponse get = httpGet("/security/userlist/" + searchText, "admin", "password1");
      assertThat("Status code for search text " + searchText,
          get.getStatusLine().getStatusCode(), CoreMatchers.equalTo(200));
      Map<String, Object> resp = gson.fromJson(
          EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
          new TypeToken<Map<String, Object>>(){}.getType());
      List<String> userList = (List) ((Map) resp.get("body")).get("users");
      assertThat("Search result size for search text " + searchText, userList.size(),
          CoreMatchers.equalTo(0));
      get.close();
    }
  }

  @Test
  void testRolesEscaped() throws IOException {
    CloseableHttpResponse get = httpGet("/security/ticket", "admin", "password1");
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>(){}.getType());
    String roles = (String) ((Map) resp.get("body")).get("roles");
    assertThat("Paramater roles", roles,
            CoreMatchers.equalTo("[\"admin\"]"));
    get.close();
  }

  private static class TestAppender extends AppenderSkeleton {
    private final List<LoggingEvent> events = new CopyOnWriteArrayList<>();

    @Override
    protected void append(LoggingEvent event) {
      events.add(event);
    }

    boolean contains(String value) {
      for (LoggingEvent event : events) {
        String message = event.getRenderedMessage();
        if (message != null && message.contains(value)) {
          return true;
        }
        String[] throwable = event.getThrowableStrRep();
        if (throwable != null) {
          for (String line : throwable) {
            if (line.contains(value)) {
              return true;
            }
          }
        }
      }
      return false;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean requiresLayout() {
      return false;
    }
  }

}
