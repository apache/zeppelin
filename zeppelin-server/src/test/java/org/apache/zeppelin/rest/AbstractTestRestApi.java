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

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
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
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.server.ZeppelinServer;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public abstract class AbstractTestRestApi {

  protected static final Logger LOG = LoggerFactory.getLogger(AbstractTestRestApi.class);

  static final String restApiUrl = "/api";
  static final String url = getUrlToTest();
  protected static final boolean wasRunning = checkIfServerIsRunning();
  static boolean pySpark = false;
  static boolean sparkR = false;
  static Gson gson = new Gson();
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
      "admin = *" +
      "[urls]\n" +
      "/api/version = anon\n" +
      "/** = authc";

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

  private static void start(boolean withAuth) throws Exception {
    if (!wasRunning) {
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(), "../");
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_WAR.getVarName(), "../zeppelin-web/dist");
      LOG.info("Staring test Zeppelin up...");
      ZeppelinConfiguration conf = ZeppelinConfiguration.create();

      if (withAuth) {
        isRunningWithAuth = true;
        // Set Anonymous session to false.
        System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_ANONYMOUS_ALLOWED.getVarName(), "false");
        
        // Create a shiro env test.
        shiroIni = new File("../conf/shiro.ini");
        if (!shiroIni.exists()) {
          shiroIni.createNewFile();
        }
        FileUtils.writeStringToFile(shiroIni, zeppelinShiro);
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
      for(InterpreterSetting intpSetting : ZeppelinServer.notebook.getInterpreterFactory().get()) {
        if (intpSetting.getName().equals("spark")) {
          sparkIntpSetting = intpSetting;
        }
      }

      Properties sparkProperties = (Properties) sparkIntpSetting.getProperties();
      // ci environment runs spark cluster for testing
      // so configure zeppelin use spark cluster
      if ("true".equals(System.getenv("CI"))) {
        // set spark master and other properties
        sparkProperties.setProperty("master", "local[2]");
        sparkProperties.setProperty("spark.cores.max", "2");
        sparkProperties.setProperty("zeppelin.spark.useHiveContext", "false");
        // set spark home for pyspark
        sparkProperties.setProperty("spark.home", getSparkHome());

        sparkIntpSetting.setProperties(sparkProperties);
        pySpark = true;
        sparkR = true;
        ZeppelinServer.notebook.getInterpreterFactory().restart(sparkIntpSetting.getId());
      } else {
        String sparkHome = getSparkHome();
        if (sparkHome != null) {
          if (System.getenv("SPARK_MASTER") != null) {
            sparkProperties.setProperty("master", System.getenv("SPARK_MASTER"));
          } else {
            sparkProperties.setProperty("master", "local[2]");
          }
          sparkProperties.setProperty("spark.cores.max", "2");
          // set spark home for pyspark
          sparkProperties.setProperty("spark.home", sparkHome);
          sparkProperties.setProperty("zeppelin.spark.useHiveContext", "false");
          pySpark = true;
          sparkR = true;
        }

        ZeppelinServer.notebook.getInterpreterFactory().restart(sparkIntpSetting.getId());
      }
    }
  }
  
  protected static void startUpWithAuthenticationEnable() throws Exception {
    start(true);
  }
  
  protected static void startUp() throws Exception {
    start(false);
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
    if (!wasRunning) {
      // restart interpreter to stop all interpreter processes
      List<String> settingList = ZeppelinServer.notebook.getInterpreterFactory()
          .getDefaultInterpreterSettingList();
      for (String setting : settingList) {
        ZeppelinServer.notebook.getInterpreterFactory().restart(setting);
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
    }
  }

  protected static boolean checkIfServerIsRunning() {
    GetMethod request = null;
    boolean isRunning = true;
    try {
      request = httpGet("/");
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
    LOG.info("Connecting to {}", url + path);
    HttpClient httpClient = new HttpClient();
    GetMethod getMethod = new GetMethod(url + path);
    getMethod.addRequestHeader("Origin", url);
    if (userAndPasswordAreNotBlank(user, pwd)) {
      getMethod.setRequestHeader("Cookie", "JSESSIONID="+ getCookie(user, pwd));
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
    java.util.regex.Matcher matcher = pattern.matcher(postMethod.getResponseHeaders("Set-Cookie")[0].toString());
    return matcher.find()? matcher.group(1) : StringUtils.EMPTY;
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
