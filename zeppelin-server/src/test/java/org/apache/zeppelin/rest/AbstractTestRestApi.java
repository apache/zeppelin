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
import org.apache.commons.io.FileUtils;
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
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.plugin.PluginManager;
import org.apache.zeppelin.utils.TestUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.server.ZeppelinServer;

public abstract class AbstractTestRestApi {
  protected static final Logger LOG = LoggerFactory.getLogger(AbstractTestRestApi.class);

  public static final String REST_API_URL = "/api";
  static final String URL = getUrlToTest();
  protected static final boolean WAS_RUNNING = checkIfServerIsRunning();
  static boolean isRunningWithAuth = false;

  private static File shiroIni = null;
  private static String zeppelinShiro =
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

  private static String zeppelinShiroKnox =
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

  private static File knoxSsoPem = null;
  private static String knoxSsoPemCertificate =
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

  protected static File zeppelinHome;
  protected static File confDir;
  protected static File notebookDir;

  private static CloseableHttpClient httpClient;

  public static CloseableHttpClient getHttpClient() {
    if (httpClient == null) {
      httpClient = HttpClients.createDefault();
    }
    return httpClient;
  }

  private String getUrl(String path) {
    String url;
    if (System.getProperty("url") != null) {
      url = System.getProperty("url");
    } else {
      url = "http://localhost:8080";
    }
    url += REST_API_URL;
    if (path != null) {
      url += path;
    }

    return url;
  }

  protected static String getUrlToTest() {
    String url = "http://localhost:8080" + REST_API_URL;
    if (System.getProperty("url") != null) {
      url = System.getProperty("url");
    }
    return url;
  }

  static ExecutorService executor;
  protected static final Runnable SERVER = new Runnable() {
    @Override
    public void run() {
      try {
        TestUtils.clearInstances();
        ZeppelinServer.main(new String[]{""});
      } catch (Throwable e) {
        LOG.error("Exception in WebDriverManager while getWebDriver ", e);
        throw new RuntimeException(e);
      }
    }
  };

  private static void start(boolean withAuth,
                            String testClassName,
                            boolean withKnox,
                            boolean cleanData)
          throws Exception {
    LOG.info("Starting ZeppelinServer withAuth: {}, testClassName: {}, withKnox: {}",
        withAuth, testClassName, withKnox);

    if (!WAS_RUNNING) {
      ZeppelinConfiguration.reset();
      // copy the resources files to a temp folder
      zeppelinHome = new File("..");
      LOG.info("ZEPPELIN_HOME: " + zeppelinHome.getAbsolutePath());
      confDir = new File(zeppelinHome, "conf_" + testClassName);
      FileUtils.deleteDirectory(confDir);
      LOG.info("ZEPPELIN_CONF_DIR: " + confDir.getAbsolutePath());
      confDir.mkdirs();

      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(),
          zeppelinHome.getAbsolutePath());
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_WAR.getVarName(),
          new File("../zeppelin-web/dist").getAbsolutePath());
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_ANGULAR_WAR.getVarName(),
              new File("../zeppelin-web-angular/dist").getAbsolutePath());
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName(),
          confDir.getAbsolutePath());
      System.setProperty(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT.getVarName(),
          "spark");

      notebookDir = new File(zeppelinHome.getAbsolutePath() + "/notebook_" + testClassName);
      if (cleanData) {
        FileUtils.deleteDirectory(notebookDir);
      }
      System.setProperty(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
          notebookDir.getPath()
      );

      // some test profile does not build zeppelin-web.
      // to prevent zeppelin starting up fail, create zeppelin-web/dist directory
      new File("../zeppelin-web/dist").mkdirs();
      new File("../zeppelin-web-angular/dist").mkdirs();

      LOG.info("Starting Zeppelin Server...");
      ZeppelinConfiguration conf = ZeppelinConfiguration.create();
      LOG.info("zconf.getClusterAddress() = {}", conf.getClusterAddress());

      if (withAuth) {
        isRunningWithAuth = true;

        // Create a shiro env test.
        shiroIni = new File(confDir, "shiro.ini");
        if (!shiroIni.exists()) {
          shiroIni.createNewFile();
        }
        if (withKnox) {
          FileUtils.writeStringToFile(shiroIni,
              zeppelinShiroKnox.replaceAll("knox-sso.pem", confDir + "/knox-sso.pem"),
              StandardCharsets.UTF_8);
          knoxSsoPem = new File(confDir, "knox-sso.pem");
          if (!knoxSsoPem.exists()) {
            knoxSsoPem.createNewFile();
          }
          FileUtils.writeStringToFile(knoxSsoPem, knoxSsoPemCertificate, StandardCharsets.UTF_8);
        } else {
          FileUtils.writeStringToFile(shiroIni, zeppelinShiro, StandardCharsets.UTF_8);
        }

      }

      executor = Executors.newSingleThreadExecutor();
      executor.submit(SERVER);
      long s = System.currentTimeMillis();
      boolean started = false;
      while (System.currentTimeMillis() - s < 1000 * 60 * 3) {  // 3 minutes
        Thread.sleep(2000);
        started = checkIfServerIsRunning();
        if (started == true) {
          break;
        }
      }
      if (started == false) {
        throw new RuntimeException("Can not start Zeppelin server");
      }

      LOG.info("Zeppelin Server is started.");
    }
  }

  protected static void startUpWithKnoxEnable(String testClassName) throws Exception {
    start(true, testClassName, true, true);
  }

  protected static void startUpWithAuthenticationEnable(String testClassName) throws Exception {
    start(true, testClassName, false, true);
  }

  protected static void startUp(String testClassName) throws Exception {
    start(false, testClassName, false, true);
  }

  protected static void startUp(String testClassName, boolean cleanData) throws Exception {
    start(false, testClassName, false, cleanData);
  }

  private static String getHostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      LOG.error("Exception in WebDriverManager while getWebDriver ", e);
      return "localhost";
    }
  }

  protected static void shutDown() throws Exception {
    shutDown(true);
  }

  protected static void shutDown(final boolean deleteConfDir) throws Exception {
    shutDown(deleteConfDir, false);
  }

  protected static void shutDown(final boolean deleteConfDir,
                                 boolean forceShutdownInterpreter) throws Exception {

    if (!WAS_RUNNING && TestUtils.getInstance(Notebook.class) != null) {
      // restart interpreter to stop all interpreter processes
      List<InterpreterSetting> settingList = TestUtils.getInstance(Notebook.class).getInterpreterSettingManager()
              .get();
      if (!TestUtils.getInstance(Notebook.class).getConf().isRecoveryEnabled() || forceShutdownInterpreter) {
        for (InterpreterSetting setting : settingList) {
          TestUtils.getInstance(Notebook.class).getInterpreterSettingManager().restart(setting.getId());
        }
      }
      if (shiroIni != null) {
        FileUtils.deleteQuietly(shiroIni);
      }
      LOG.info("Terminating Zeppelin Server...");
      //ZeppelinServer.jettyWebServer.stop();
      executor.shutdown();
      executor.shutdownNow();
      //PluginManager.reset();
      //ZeppelinConfiguration.reset();

      long s = System.currentTimeMillis();
      boolean started = true;
      while (System.currentTimeMillis() - s < 1000 * 60 * 3) {  // 3 minutes
        Thread.sleep(2000);
        started = checkIfServerIsRunning();
        if (started == false) {
          break;
        }
      }
      if (started == true) {
        throw new RuntimeException("Can not stop Zeppelin server");
      }

      LOG.info("Zeppelin Server is terminated.");

      if (isRunningWithAuth) {
        isRunningWithAuth = shiroIni.exists();
      }

      if (deleteConfDir && !TestUtils.getInstance(Notebook.class).getConf().isRecoveryEnabled()) {
        // don't delete interpreter.json when recovery is enabled. otherwise the interpreter setting
        // id will change after zeppelin restart, then we can not recover interpreter process
        // properly
        FileUtils.deleteDirectory(confDir);
      }
      TestUtils.clearInstances();
    }
  }

  public static boolean checkIfServerIsRunning() {
    boolean isRunning;
    try (CloseableHttpResponse response = httpGet("/version")) {
      isRunning = response.getStatusLine().getStatusCode() == 200;
    } catch (IOException e) {
      LOG.error("AbstractTestRestApi.checkIfServerIsRunning() fails .. ZeppelinServer is not " +
          "running");
      isRunning = false;
    }
    return isRunning;
  }

  public static CloseableHttpResponse httpGet(String path) throws IOException {
    return httpGet(path, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  public static CloseableHttpResponse httpGet(String path, String user, String pwd) throws IOException {
    return httpGet(path, user, pwd, StringUtils.EMPTY);
  }

  public static CloseableHttpResponse httpGet(String path, String user, String pwd, String cookies)
    throws IOException {
    LOG.info("Connecting to {}", URL + path);
    HttpGet httpGet = new HttpGet(URL + path);
    httpGet.addHeader("Origin", URL);
    if (userAndPasswordAreNotBlank(user, pwd)) {
      httpGet.setHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    if (!StringUtils.isBlank(cookies)) {
      httpGet.setHeader("Cookie", httpGet.getFirstHeader("Cookie") + ";" + cookies);
    }
    CloseableHttpResponse response = getHttpClient().execute(httpGet);
    LOG.info("{} - {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
    return response;
  }

  public static CloseableHttpResponse httpDelete(String path) throws IOException {
    return httpDelete(path, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  public static CloseableHttpResponse httpDelete(String path, String user, String pwd)
      throws IOException {
    LOG.info("Connecting to {}", URL + path);
    HttpDelete httpDelete = new HttpDelete(URL + path);
    httpDelete.addHeader("Origin", URL);
    if (userAndPasswordAreNotBlank(user, pwd)) {
      httpDelete.setHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    CloseableHttpResponse response = getHttpClient().execute(httpDelete);
    LOG.info("{} - {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
    return response;
  }

  public static CloseableHttpResponse httpPost(String path, String body) throws IOException {
    return httpPost(path, body, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  public static CloseableHttpResponse httpPost(String path, String request, String user, String pwd)
      throws IOException {
    LOG.info("Connecting to {}", URL + path);
    RequestConfig localConfig = RequestConfig.custom()
      .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
      .build();

    HttpPost httpPost = new HttpPost(URL + path);
    httpPost.setConfig(localConfig);
    httpPost.setEntity(new StringEntity(request, ContentType.APPLICATION_JSON));
    if (userAndPasswordAreNotBlank(user, pwd)) {
      httpPost.setHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    CloseableHttpResponse response = getHttpClient().execute(httpPost);
    LOG.info("{} - {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
    return response;
  }

  public static CloseableHttpResponse httpPut(String path, String body) throws IOException {
    return httpPut(path, body, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  public static CloseableHttpResponse httpPut(String path, String body, String user, String pwd)
      throws IOException {
    LOG.info("Connecting to {}", URL + path);
    HttpPut httpPut = new HttpPut(URL + path);
    httpPut.addHeader("Origin", URL);
    httpPut.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
    if (userAndPasswordAreNotBlank(user, pwd)) {
      httpPut.setHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    CloseableHttpResponse response = getHttpClient().execute(httpPut);
    LOG.info("{} - {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
    return response;
  }

  private static String getCookie(String user, String password) throws IOException {
    HttpPost httpPost = new HttpPost(URL + "/login");
    httpPost.addHeader("Origin", URL);
    ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
    postParameters.add(new BasicNameValuePair("password", password));
    postParameters.add(new BasicNameValuePair("userName", user));
    httpPost.setEntity(new UrlEncodedFormEntity(postParameters, StandardCharsets.UTF_8));
    try (CloseableHttpResponse response = getHttpClient().execute(httpPost)) {
      LOG.info("{} - {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
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
          LOG.error("Exception in AbstractTestRestApi while matchesSafely ", e);
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
      LOG.error(e.getMessage(), e);
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
