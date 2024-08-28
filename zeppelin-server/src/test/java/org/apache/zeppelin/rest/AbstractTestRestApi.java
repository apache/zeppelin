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

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.apache.zeppelin.conf.ZeppelinConfiguration;

public abstract class AbstractTestRestApi {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTestRestApi.class);

  public static final String REST_API_URL = "/api";
  protected ZeppelinConfiguration zConf;

  protected static final String ZEPPELIN_SHIRO =
      "[users]\n" +
          "admin = password1, admin\n" +
          "user1 = password2, role1, role2\n" +
          "user2 = password3, role3\n" +
          "user3 = password4, role2\n" +
          "[main]\n" +
          "sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager\n" +
          "securityManager.sessionManager = $sessionManager\n" +
          "securityManager.sessionManager.globalSessionTimeout = 86400000\n" +
          "shiro.loginUrl = /api/login\n" +
          "[roles]\n" +
          "role1 = *\n" +
          "role2 = *\n" +
          "role3 = *\n" +
          "admin = *\n" +
          "[urls]\n" +
          "/api/version = anon\n" +
          "/api/cluster/address = anon\n" +
          "/** = authc";

  protected static final String ZEPPELIN_SHIRO_KNOX =
           "[main]\n" +
          "knoxJwtRealm = org.apache.zeppelin.realm.jwt.KnoxJwtRealm\n" +
          "knoxJwtRealm.providerUrl = https://domain.example.com/\n" +
          "knoxJwtRealm.login = gateway/knoxsso/knoxauth/login.html\n" +
          "knoxJwtRealm.logout = gateway/knoxssout/api/v1/webssout\n" +
          "knoxJwtRealm.redirectParam = originalUrl\n" +
          "knoxJwtRealm.cookieName = hadoop-jwt\n" +
          "knoxJwtRealm.publicKeyPath = knox-sso.pem\n" +
          "authc = org.apache.zeppelin.realm.jwt.KnoxAuthenticationFilter\n" +
          "sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager\n" +
          "securityManager.sessionManager = $sessionManager\n" +
          "securityManager.sessionManager.globalSessionTimeout = 86400000\n" +
          "shiro.loginUrl = /api/login\n" +
          "[roles]\n" +
          "admin = *\n" +
          "[urls]\n" +
          "/api/version = anon\n" +
          "/api/cluster/address = anon\n" +
          "/** = authc";

  protected static final String KNOW_SSO_PEM_CERTIFICATE =
      "-----BEGIN CERTIFICATE-----\n"
          + "MIIChjCCAe+gAwIBAgIJALYrdDEXKwcqMA0GCSqGSIb3DQEBBQUAMIGEMQswCQYD\n"
          + "VQQGEwJVUzENMAsGA1UECBMEVGVzdDENMAsGA1UEBxMEVGVzdDEPMA0GA1UEChMG\n"
          + "SGFkb29wMQ0wCwYDVQQLEwRUZXN0MTcwNQYDVQQDEy5jdHItZTEzNS0xNTEyMDY5\n"
          + "MDMyOTc1LTU0NDctMDEtMDAwMDAyLmh3eC5zaXRlMB4XDTE3MTIwNDA5NTIwMFoX\n"
          + "DTE4MTIwNDA5NTIwMFowgYQxCzAJBgNVBAYTAlVTMQ0wCwYDVQQIEwRUZXN0MQ0w\n"
          + "CwYDVQQHEwRUZXN0MQ8wDQYDVQQKEwZIYWRvb3AxDTALBgNVBAsTBFRlc3QxNzA1\n"
          + "BgNVBAMTLmN0ci1lMTM1LTE1MTIwNjkwMzI5NzUtNTQ0Ny0wMS0wMDAwMDIuaHd4\n"
          + "LnNpdGUwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAILFoXdz3yCy2INncYM2\n"
          + "y72fYrONoQIxeeIzeJIibXLTuowSju90Q6aThSyUsQ6NEia2flnlKiCgINTNAodh\n"
          + "UPUVGyGT+NMrqJzzpXAll2UUa6gIUPnXYEzYNkMIpbQOAo5BAg7YamaidbPPiT3W\n"
          + "wAD1rWo3AMUY+nZJrAi4dEH5AgMBAAEwDQYJKoZIhvcNAQEFBQADgYEAB0R07/lo\n"
          + "4hD+WeDEeyLTnsbFnPNXxBT1APMUmmuCjcky/19ZB8OphqTKIITONdOK/XHdjZHG\n"
          + "JDOfhBkVknL42lSi45ahUAPS2PZOlQL08MbS8xajP1faterm+aHcdwJVK9dK76RB\n"
          + "/bA8TFNPblPxavIOcd+R+RfFmT1YKfYIhco=\n"
          + "-----END CERTIFICATE-----";

  private static CloseableHttpClient httpClient;

  public static CloseableHttpClient getHttpClient() {
    if (httpClient == null) {
      httpClient = HttpClients.createDefault();
    }
    return httpClient;
  }

  protected static String getUrlToTest(ZeppelinConfiguration zConf) {
    return "http://localhost:" + zConf.getServerPort() + REST_API_URL;
  }

  public CloseableHttpResponse httpGet(String path)
      throws IOException {
    return httpGet(path, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  public CloseableHttpResponse httpGet(String path, String user, String pwd)
      throws IOException {
    return httpGet(path, user, pwd, StringUtils.EMPTY);
  }

  public CloseableHttpResponse httpGet(String path, String user, String pwd, String cookies)
    throws IOException {
    LOGGER.info("Connecting to {}", getUrlToTest(zConf) + path);
    HttpGet httpGet = new HttpGet(getUrlToTest(zConf) + path);
    httpGet.addHeader("Origin", getUrlToTest(zConf));
    if (userAndPasswordAreNotBlank(user, pwd)) {
      httpGet.setHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    if (!StringUtils.isBlank(cookies)) {
      httpGet.setHeader("Cookie", httpGet.getFirstHeader("Cookie") + ";" + cookies);
    }
    CloseableHttpResponse response = getHttpClient().execute(httpGet);
    LOGGER.info("{} - {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
    return response;
  }

  public CloseableHttpResponse httpDelete(String path)
      throws IOException {
    return httpDelete(path, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  public CloseableHttpResponse httpDelete(String path, String user, String pwd)
      throws IOException {
    LOGGER.info("Connecting to {}", getUrlToTest(zConf) + path);
    HttpDelete httpDelete = new HttpDelete(getUrlToTest(zConf) + path);
    httpDelete.addHeader("Origin", getUrlToTest(zConf));
    if (userAndPasswordAreNotBlank(user, pwd)) {
      httpDelete.setHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    CloseableHttpResponse response = getHttpClient().execute(httpDelete);
    LOGGER.info("{} - {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
    return response;
  }

  public CloseableHttpResponse httpPost(String path, String body)
      throws IOException {
    return httpPost(path, body, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  public CloseableHttpResponse httpPost(String path, String request, String user, String pwd)
      throws IOException {
    LOGGER.info("Connecting to {}", getUrlToTest(zConf) + path);
    RequestConfig localConfig = RequestConfig.custom()
      .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
      .build();

    HttpPost httpPost = new HttpPost(getUrlToTest(zConf) + path);
    httpPost.setConfig(localConfig);
    httpPost.setEntity(new StringEntity(request, ContentType.APPLICATION_JSON));
    if (userAndPasswordAreNotBlank(user, pwd)) {
      httpPost.setHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    CloseableHttpResponse response = getHttpClient().execute(httpPost);
    LOGGER.info("{} - {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
    return response;
  }

  public CloseableHttpResponse httpPut(String path, String body)
      throws IOException {
    return httpPut(path, body, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  public CloseableHttpResponse httpPut(String path, String body, String user, String pwd)
      throws IOException {
    LOGGER.info("Connecting to {}", getUrlToTest(zConf) + path);
    HttpPut httpPut = new HttpPut(getUrlToTest(zConf) + path);
    httpPut.addHeader("Origin", getUrlToTest(zConf));
    httpPut.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
    if (userAndPasswordAreNotBlank(user, pwd)) {
      httpPut.setHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    CloseableHttpResponse response = getHttpClient().execute(httpPut);
    LOGGER.info("{} - {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
    return response;
  }

  private String getCookie(String user, String password)
      throws IOException {
    HttpPost httpPost = new HttpPost(getUrlToTest(zConf) + "/login");
    httpPost.addHeader("Origin", getUrlToTest(zConf));
    ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
    postParameters.add(new BasicNameValuePair("password", password));
    postParameters.add(new BasicNameValuePair("userName", user));
    httpPost.setEntity(new UrlEncodedFormEntity(postParameters, StandardCharsets.UTF_8));
    try (CloseableHttpResponse response = getHttpClient().execute(httpPost)) {
      LOGGER.info("{} - {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
      Pattern pattern = Pattern.compile("JSESSIONID=([a-zA-Z0-9-]*)");
      Header[] setCookieHeaders = response.getHeaders("Set-Cookie");
      String jsessionId = null;
      for (Header setCookie : setCookieHeaders) {
        java.util.regex.Matcher matcher = pattern.matcher(setCookie.toString());
        if (matcher.find()) {
          jsessionId = matcher.group(1);
        }
      }
      if (jsessionId != null) {
        return jsessionId;
      } else {
        return StringUtils.EMPTY;
      }
    }
  }

  protected static boolean userAndPasswordAreNotBlank(String user, String pwd) {
    return StringUtils.isNoneBlank(user, pwd);
  }

  protected static Matcher<HttpResponse> responsesWith(final int expectedStatusCode) {
    return new TypeSafeMatcher<HttpResponse>() {
      WeakReference<HttpResponse> response;

      @Override
      public boolean matchesSafely(HttpResponse httpResponse) {
        response = (response == null) ? new WeakReference<>(httpResponse) : response;
        return httpResponse.getStatusLine().getStatusCode() == expectedStatusCode;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("HTTP response ").appendValue(expectedStatusCode);
      }

      @Override
      protected void describeMismatchSafely(HttpResponse item, Description description) {
        description.appendText("got ").appendValue(item.getStatusLine().getStatusCode());
      }
    };
  }

  protected TypeSafeMatcher<String> isJSON() {
    return new TypeSafeMatcher<String>() {
      @Override
      public boolean matchesSafely(String body) {
        String b = body.trim();
        return (b.startsWith("{") && b.endsWith("}")) || (b.startsWith("[") && b.endsWith("]"));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("response in JSON format ");
      }

      @Override
      protected void describeMismatchSafely(String item, Description description) {
        description.appendText("got ").appendText(item);
      }
    };
  }

  protected TypeSafeMatcher<String> isValidJSON() {
    return new TypeSafeMatcher<String>() {
      @Override
      public boolean matchesSafely(String body) {
        boolean isValid = true;
        try {
          new JsonParser().parse(body);
        } catch (JsonParseException e) {
          LOGGER.error("Exception in AbstractTestRestApi while matchesSafely ", e);
          isValid = false;
        }
        return isValid;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("response in JSON format ");
      }

      @Override
      protected void describeMismatchSafely(String item, Description description) {
        description.appendText("got ").appendText(item);
      }
    };
  }

  protected TypeSafeMatcher<? super JsonElement> hasRootElementNamed(final String memberName) {
    return new TypeSafeMatcher<JsonElement>() {
      @Override
      protected boolean matchesSafely(JsonElement item) {
        return item.isJsonObject() && item.getAsJsonObject().has(memberName);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("response in JSON format with \"").appendText(memberName)
            .appendText("\" beeing a root element ");
      }

      @Override
      protected void describeMismatchSafely(JsonElement root, Description description) {
        description.appendText("got ").appendText(root.toString());
      }
    };
  }

  public static void ps() {
    DefaultExecutor executor = new DefaultExecutor();
    executor.setStreamHandler(new PumpStreamHandler(System.out, System.err));

    CommandLine cmd = CommandLine.parse("ps");
    cmd.addArgument("aux", false);

    try {
      executor.execute(cmd);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  /**
   * Status code matcher.
   */
  public static Matcher<? super HttpResponse> isForbidden() {
    return responsesWith(HttpStatus.SC_FORBIDDEN);
  }

  public static Matcher<? super HttpResponse> isAllowed() {
    return responsesWith(HttpStatus.SC_OK);
  }

  public static Matcher<? super HttpResponse> isCreated() {
    return responsesWith(HttpStatus.SC_CREATED);
  }

  public static Matcher<? super HttpResponse> isBadRequest() {
    return responsesWith(HttpStatus.SC_BAD_REQUEST);
  }

  public static Matcher<? super HttpResponse> isNotFound() {
    return responsesWith(HttpStatus.SC_NOT_FOUND);
  }

  public static Matcher<? super HttpResponse> isNotAllowed() {
    return responsesWith(HttpStatus.SC_METHOD_NOT_ALLOWED);
  }

  public static Matcher<? super HttpResponse> isExpectationFailed() {
    return responsesWith(HttpStatus.SC_EXPECTATION_FAILED);
  }
}
