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
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterProperty;
import org.apache.zeppelin.interpreter.InterpreterPropertyType;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.server.ZeppelinServer;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTestRestApi {

  protected static final Logger LOG = LoggerFactory.getLogger(AbstractTestRestApi.class);

  static final String restApiUrl = "/api";
  static final String url = getUrlToTest();
  protected static final boolean wasRunning = checkIfServerIsRunning();
  static boolean pySpark = false;
  static boolean sparkR = false;
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
      "/** = authc";

  private static String zeppelinShiroKnox =
      "[users]\n" +
      "admin = password1, admin\n" +
      "user1 = password2, role1, role2\n" +
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
      "/** = authc";

  private static File knoxSsoPem = null;
  private static String KNOX_SSO_PEM =
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

  private String getUrl(String path) {
    String url;
    if (System.getProperty("url") != null) {
      url = System.getProperty("url");
    } else {
      url = "http://localhost:8080";
    }
    url += restApiUrl;
    if (path != null)
      url += path;
    return url;
  }

  protected static String getUrlToTest() {
    String url = "http://localhost:8080" + restApiUrl;
    if (System.getProperty("url") != null) {
      url = System.getProperty("url");
    }
    return url;
  }

  static ExecutorService executor;
  protected static final Runnable server = new Runnable() {
    @Override
    public void run() {
      try {
        ZeppelinServer.main(new String[] {""});
      } catch (Exception e) {
        LOG.error("Exception in WebDriverManager while getWebDriver ", e);
        throw new RuntimeException(e);
      }
    }
  };

  private static void start(boolean withAuth, String testClassName, boolean withKnox) throws Exception {
    if (!wasRunning) {
      // copy the resources files to a temp folder
      zeppelinHome = new File("..");
      LOG.info("ZEPPELIN_HOME: " + zeppelinHome.getAbsolutePath());
      confDir = new File(zeppelinHome, "conf_" + testClassName);
      confDir.mkdirs();

      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(), zeppelinHome.getAbsolutePath());
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_WAR.getVarName(), new File("../zeppelin-web/dist").getAbsolutePath());
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName(), confDir.getAbsolutePath());

      // some test profile does not build zeppelin-web.
      // to prevent zeppelin starting up fail, create zeppelin-web/dist directory
      new File("../zeppelin-web/dist").mkdirs();

      LOG.info("Staring test Zeppelin up...");
      ZeppelinConfiguration conf = ZeppelinConfiguration.create();

      if (withAuth) {
        isRunningWithAuth = true;
        // Set Anonymous session to false.
        System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_ANONYMOUS_ALLOWED.getVarName(), "false");
        
        // Create a shiro env test.
        shiroIni = new File(confDir, "shiro.ini");
        if (!shiroIni.exists()) {
          shiroIni.createNewFile();
        }
        if (withKnox) {
          FileUtils.writeStringToFile(shiroIni,
              zeppelinShiroKnox.replaceAll("knox-sso.pem", confDir + "/knox-sso.pem"));
          knoxSsoPem = new File(confDir, "knox-sso.pem");
          if (!knoxSsoPem.exists()) {
            knoxSsoPem.createNewFile();
          }
          FileUtils.writeStringToFile(knoxSsoPem, KNOX_SSO_PEM);
        } else {
          FileUtils.writeStringToFile(shiroIni, zeppelinShiro);
        }

      }

      // exclude org.apache.zeppelin.rinterpreter.* for scala 2.11 test
      String interpreters = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETERS);
      String interpretersCompatibleWithScala211Test = null;

      for (String intp : interpreters.split(",")) {
        if (intp.startsWith("org.apache.zeppelin.rinterpreter")) {
          continue;
        }

        if (interpretersCompatibleWithScala211Test == null) {
          interpretersCompatibleWithScala211Test = intp;
        } else {
          interpretersCompatibleWithScala211Test += "," + intp;
        }
      }

      System.setProperty(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETERS.getVarName(),
          interpretersCompatibleWithScala211Test);


      executor = Executors.newSingleThreadExecutor();
      executor.submit(server);
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
      LOG.info("Test Zeppelin stared.");


      // assume first one is spark
      InterpreterSetting sparkIntpSetting = null;
      for(InterpreterSetting intpSetting :
          ZeppelinServer.notebook.getInterpreterSettingManager().get()) {
        if (intpSetting.getName().equals("spark")) {
          sparkIntpSetting = intpSetting;
        }
      }

      Map<String, InterpreterProperty> sparkProperties =
          (Map<String, InterpreterProperty>) sparkIntpSetting.getProperties();
      // ci environment runs spark cluster for testing
      // so configure zeppelin use spark cluster
      if ("true".equals(System.getenv("CI"))) {
        // set spark master and other properties
        sparkProperties.put("master",
            new InterpreterProperty("master", "local[2]", InterpreterPropertyType.TEXTAREA.getValue()));
        sparkProperties.put("spark.cores.max",
            new InterpreterProperty("spark.cores.max", "2", InterpreterPropertyType.TEXTAREA.getValue()));
        sparkProperties.put("zeppelin.spark.useHiveContext",
            new InterpreterProperty("zeppelin.spark.useHiveContext", false, InterpreterPropertyType.CHECKBOX.getValue()));
        // set spark home for pyspark
        sparkProperties.put("spark.home",
            new InterpreterProperty("spark.home", getSparkHome(), InterpreterPropertyType.TEXTAREA.getValue()));
        sparkProperties.put("zeppelin.pyspark.useIPython",  new InterpreterProperty("zeppelin.pyspark.useIPython", "false", InterpreterPropertyType.TEXTAREA.getValue()));

        sparkIntpSetting.setProperties(sparkProperties);
        pySpark = true;
        sparkR = true;
        ZeppelinServer.notebook.getInterpreterSettingManager().restart(sparkIntpSetting.getId());
      } else {
        String sparkHome = getSparkHome();
        if (sparkHome != null) {
          if (System.getenv("SPARK_MASTER") != null) {
            sparkProperties.put("master",
                new InterpreterProperty("master", System.getenv("SPARK_MASTER"), InterpreterPropertyType.TEXTAREA.getValue()));
          } else {
            sparkProperties.put("master",
                new InterpreterProperty("master", "local[2]", InterpreterPropertyType.TEXTAREA.getValue()));
          }
          sparkProperties.put("spark.cores.max",
              new InterpreterProperty("spark.cores.max", "2", InterpreterPropertyType.TEXTAREA.getValue()));
          // set spark home for pyspark
          sparkProperties.put("spark.home",
              new InterpreterProperty("spark.home", sparkHome, InterpreterPropertyType.TEXTAREA.getValue()));
          sparkProperties.put("zeppelin.spark.useHiveContext",
              new InterpreterProperty("zeppelin.spark.useHiveContext", false, InterpreterPropertyType.CHECKBOX.getValue()));
          sparkProperties.put("zeppelin.pyspark.useIPython",  new InterpreterProperty("zeppelin.pyspark.useIPython", "false", InterpreterPropertyType.TEXTAREA.getValue()));

          pySpark = true;
          sparkR = true;
        }

        ZeppelinServer.notebook.getInterpreterSettingManager().restart(sparkIntpSetting.getId());
      }
    }
  }

  protected static void startUpWithKnoxEnable(String testClassName) throws Exception {
    start(true, testClassName, true);
  }
  
  protected static void startUpWithAuthenticationEnable(String testClassName) throws Exception {
    start(true, testClassName, false);
  }
  
  protected static void startUp(String testClassName) throws Exception {
    start(false, testClassName, false);
  }

  private static String getHostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      LOG.error("Exception in WebDriverManager while getWebDriver ", e);
      return "localhost";
    }
  }

  private static String getSparkHome() {
    String sparkHome = System.getenv("SPARK_HOME");
    if (sparkHome != null) {
      return sparkHome;
    }
    sparkHome = getSparkHomeRecursively(new File(System.getProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName())));
    System.out.println("SPARK HOME detected " + sparkHome);
    return sparkHome;
  }

  boolean isPyspark() {
    return pySpark;
  }

  boolean isSparkR() {
    return sparkR;
  }

  private static String getSparkHomeRecursively(File dir) {
    if (dir == null) return null;
    File files []  = dir.listFiles();
    if (files == null) return null;

    File homeDetected = null;
    for (File f : files) {
      if (isActiveSparkHome(f)) {
        homeDetected = f;
        break;
      }
    }

    if (homeDetected != null) {
      return homeDetected.getAbsolutePath();
    } else {
      return getSparkHomeRecursively(dir.getParentFile());
    }
  }

  private static boolean isActiveSparkHome(File dir) {
    return dir.getName().matches("spark-[0-9\\.]+[A-Za-z-]*-bin-hadoop[0-9\\.]+");
  }

  protected static void shutDown() throws Exception {
    shutDown(true);
  }

  protected static void shutDown(final boolean deleteConfDir) throws Exception {
    if (!wasRunning) {
      // restart interpreter to stop all interpreter processes
      List<InterpreterSetting> settingList = ZeppelinServer.notebook.getInterpreterSettingManager().get();
      if (!ZeppelinServer.notebook.getConf().isRecoveryEnabled()) {
        for (InterpreterSetting setting : settingList) {
          ZeppelinServer.notebook.getInterpreterSettingManager().restart(setting.getId());
        }
      }
      if (shiroIni != null) {
        FileUtils.deleteQuietly(shiroIni);
      }
      LOG.info("Terminating test Zeppelin...");
      ZeppelinServer.jettyWebServer.stop();
      executor.shutdown();

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

      LOG.info("Test Zeppelin terminated.");

      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETERS.getVarName());
      if (isRunningWithAuth) {
        isRunningWithAuth = false;
        System
            .clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_ANONYMOUS_ALLOWED.getVarName());
      }

      if (deleteConfDir && !ZeppelinServer.notebook.getConf().isRecoveryEnabled()) {
        // don't delete interpreter.json when recovery is enabled. otherwise the interpreter setting
        // id will change after zeppelin restart, then we can not recover interpreter process
        // properly
        FileUtils.deleteDirectory(confDir);
      }
    }
  }

  protected static boolean checkIfServerIsRunning() {
    GetMethod request = null;
    boolean isRunning = true;
    try {
      request = httpGet("/version");
      isRunning = request.getStatusCode() == 200;
    } catch (IOException e) {
      LOG.error("AbstractTestRestApi.checkIfServerIsRunning() fails .. ZeppelinServer is not running");
      isRunning = false;
    } finally {
      if (request != null) {
        request.releaseConnection();
      }
    }
    return isRunning;
  }

  protected static GetMethod httpGet(String path) throws IOException {
    return httpGet(path, StringUtils.EMPTY, StringUtils.EMPTY);
  }
  
  protected static GetMethod httpGet(String path, String user, String pwd) throws IOException {
    return httpGet(path, user, pwd, StringUtils.EMPTY);
  }

  protected static GetMethod httpGet(String path, String user, String pwd, String cookies) throws IOException {
    LOG.info("Connecting to {}", url + path);
    HttpClient httpClient = new HttpClient();
    GetMethod getMethod = new GetMethod(url + path);
    getMethod.addRequestHeader("Origin", url);
    if (userAndPasswordAreNotBlank(user, pwd)) {
      getMethod.setRequestHeader("Cookie", "JSESSIONID="+ getCookie(user, pwd));
    }
    if (!StringUtils.isBlank(cookies)) {
      getMethod.setRequestHeader("Cookie", getMethod.getResponseHeader("Cookie") + ";" + cookies);
    }
    httpClient.executeMethod(getMethod);
    LOG.info("{} - {}", getMethod.getStatusCode(), getMethod.getStatusText());
    return getMethod;
  }

  protected static DeleteMethod httpDelete(String path) throws IOException {
    return httpDelete(path, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  protected static DeleteMethod httpDelete(String path, String user, String pwd) throws IOException {
    LOG.info("Connecting to {}", url + path);
    HttpClient httpClient = new HttpClient();
    DeleteMethod deleteMethod = new DeleteMethod(url + path);
    deleteMethod.addRequestHeader("Origin", url);
    if (userAndPasswordAreNotBlank(user, pwd)) {
      deleteMethod.setRequestHeader("Cookie", "JSESSIONID="+ getCookie(user, pwd));
    }
    httpClient.executeMethod(deleteMethod);
    LOG.info("{} - {}", deleteMethod.getStatusCode(), deleteMethod.getStatusText());
    return deleteMethod;
  }

  protected static PostMethod httpPost(String path, String body) throws IOException {
    return httpPost(path, body, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  protected static PostMethod httpPost(String path, String request, String user, String pwd)
      throws IOException {
    LOG.info("Connecting to {}", url + path);
    HttpClient httpClient = new HttpClient();
    PostMethod postMethod = new PostMethod(url + path);
    postMethod.setRequestBody(request);
    postMethod.getParams().setCookiePolicy(CookiePolicy.IGNORE_COOKIES);
    if (userAndPasswordAreNotBlank(user, pwd)) {
      postMethod.setRequestHeader("Cookie", "JSESSIONID="+ getCookie(user, pwd));
    }
    httpClient.executeMethod(postMethod);
    LOG.info("{} - {}", postMethod.getStatusCode(), postMethod.getStatusText());
    return postMethod;
  }

  protected static PutMethod httpPut(String path, String body) throws IOException {
    return httpPut(path, body, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  protected static PutMethod httpPut(String path, String body, String user, String pwd) throws IOException {
    LOG.info("Connecting to {}", url + path);
    HttpClient httpClient = new HttpClient();
    PutMethod putMethod = new PutMethod(url + path);
    putMethod.addRequestHeader("Origin", url);
    RequestEntity entity = new ByteArrayRequestEntity(body.getBytes("UTF-8"));
    putMethod.setRequestEntity(entity);
    if (userAndPasswordAreNotBlank(user, pwd)) {
      putMethod.setRequestHeader("Cookie", "JSESSIONID="+ getCookie(user, pwd));
    }
    httpClient.executeMethod(putMethod);
    LOG.info("{} - {}", putMethod.getStatusCode(), putMethod.getStatusText());
    return putMethod;
  }

  private static String getCookie(String user, String password) throws IOException {
    HttpClient httpClient = new HttpClient();
    PostMethod postMethod = new PostMethod(url + "/login");
    postMethod.addRequestHeader("Origin", url);
    postMethod.setParameter("password", password);
    postMethod.setParameter("userName", user);
    httpClient.executeMethod(postMethod);
    LOG.info("{} - {}", postMethod.getStatusCode(), postMethod.getStatusText());
    Pattern pattern = Pattern.compile("JSESSIONID=([a-zA-Z0-9-]*)");
    Header[] setCookieHeaders = postMethod.getResponseHeaders("Set-Cookie");
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

  protected static boolean userAndPasswordAreNotBlank(String user, String pwd) {
    if (StringUtils.isBlank(user) && StringUtils.isBlank(pwd)) {
      return false;
    }
    return true;
  }
  
  protected Matcher<HttpMethodBase> responsesWith(final int expectedStatusCode) {
    return new TypeSafeMatcher<HttpMethodBase>() {
      WeakReference<HttpMethodBase> method;

      @Override
      public boolean matchesSafely(HttpMethodBase httpMethodBase) {
        method = (method == null) ? new WeakReference<>(httpMethodBase) : method;
        return httpMethodBase.getStatusCode() == expectedStatusCode;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("HTTP response ").appendValue(expectedStatusCode)
            .appendText(" from ").appendText(method.get().getPath());
      }

      @Override
      protected void describeMismatchSafely(HttpMethodBase item, Description description) {
        description.appendText("got ").appendValue(item.getStatusCode()).appendText(" ")
            .appendText(item.getStatusText());
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


  /** Status code matcher */
  protected Matcher<? super HttpMethodBase> isForbidden() { return responsesWith(403); }

  protected Matcher<? super HttpMethodBase> isAllowed() {
    return responsesWith(200);
  }

  protected Matcher<? super HttpMethodBase> isCreated() { return responsesWith(201); }

  protected Matcher<? super HttpMethodBase> isBadRequest() { return responsesWith(400); }

  protected Matcher<? super HttpMethodBase> isNotFound() { return responsesWith(404); }

  protected Matcher<? super HttpMethodBase> isNotAllowed() {
    return responsesWith(405);
  }

}
