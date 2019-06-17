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
package org.apache.zeppelin.cluster;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.plugin.PluginManager;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.utils.TestUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class ZeppelinServerMock {
  protected static final Logger LOG = LoggerFactory.getLogger(ZeppelinServerMock.class);

  static final String REST_API_URL = "/api";
  static final String URL = getUrlToTest();
  protected static final boolean WAS_RUNNING = checkIfServerIsRunning();

  protected static File zeppelinHome;
  protected static File confDir;
  protected static File notebookDir;

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
      } catch (Exception e) {
        LOG.error("Exception in WebDriverManager while getWebDriver ", e);
        throw new RuntimeException(e);
      }
    }
  };

  private static void start(String testClassName, boolean cleanData, ZeppelinConfiguration zconf)
      throws Exception {
    LOG.info("Starting ZeppelinServer testClassName: {}", testClassName);

    if (!WAS_RUNNING) {
      // copy the resources files to a temp folder
      zeppelinHome = new File("..");
      LOG.info("ZEPPELIN_HOME: " + zeppelinHome.getAbsolutePath());
      confDir = new File(zeppelinHome, "conf_" + testClassName);
      confDir.mkdirs();
      zconf.save(confDir + "/zeppelin-site.xml");

      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(),
          zeppelinHome.getAbsolutePath());
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_WAR.getVarName(),
          new File("../zeppelin-web/dist").getAbsolutePath());
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

      LOG.info("Staring test Zeppelin up...");
      ZeppelinConfiguration conf = ZeppelinConfiguration.create();

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
      //ZeppelinServer.notebook.setParagraphJobListener(NotebookServer.getInstance());
      LOG.info("Test Zeppelin stared.");
    }
  }

  protected static void startUp(String testClassName, ZeppelinConfiguration zconf) throws Exception {
    start(testClassName, true, zconf);
  }

  protected static void shutDown() throws Exception {
    shutDown(true);
  }

  protected static void shutDown(final boolean deleteConfDir) throws Exception {
    if (!WAS_RUNNING && TestUtils.getInstance(Notebook.class) != null) {
      // restart interpreter to stop all interpreter processes
      List<InterpreterSetting> settingList = TestUtils.getInstance(Notebook.class).getInterpreterSettingManager()
          .get();
      if (!TestUtils.getInstance(Notebook.class).getConf().isRecoveryEnabled()) {
        for (InterpreterSetting setting : settingList) {
          TestUtils.getInstance(Notebook.class).getInterpreterSettingManager().restart(setting.getId());
        }
      }
      LOG.info("Terminating test Zeppelin...");
      ZeppelinServer.jettyWebServer.stop();
      executor.shutdown();
      PluginManager.reset();

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

      if (deleteConfDir && !TestUtils.getInstance(Notebook.class).getConf().isRecoveryEnabled()) {
        // don't delete interpreter.json when recovery is enabled. otherwise the interpreter setting
        // id will change after zeppelin restart, then we can not recover interpreter process
        // properly
        FileUtils.deleteDirectory(confDir);
      }
    }

  }

  protected static boolean checkIfServerIsRunning() {
    GetMethod request = null;
    boolean isRunning;
    try {
      request = httpGet("/version");
      isRunning = request.getStatusCode() == 200;
    } catch (IOException e) {
      LOG.error("AbstractTestRestApi.checkIfServerIsRunning() fails .. ZeppelinServer is not " +
          "running");
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

  protected static GetMethod httpGet(String path, String user, String pwd, String cookies)
      throws IOException {
    LOG.info("Connecting to {}", URL + path);
    HttpClient httpClient = new HttpClient();
    GetMethod getMethod = new GetMethod(URL + path);
    getMethod.addRequestHeader("Origin", URL);
    if (userAndPasswordAreNotBlank(user, pwd)) {
      getMethod.setRequestHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    if (!StringUtils.isBlank(cookies)) {
      getMethod.setRequestHeader("Cookie", getMethod.getResponseHeader("Cookie") + ";" + cookies);
    }
    httpClient.executeMethod(getMethod);
    LOG.info("{} - {}", getMethod.getStatusCode(), getMethod.getStatusText());
    return getMethod;
  }

  protected static PutMethod httpPut(String path, String body) throws IOException {
    return httpPut(path, body, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  protected static PutMethod httpPut(String path, String body, String user, String pwd)
      throws IOException {
    LOG.info("Connecting to {}", URL + path);
    HttpClient httpClient = new HttpClient();
    PutMethod putMethod = new PutMethod(URL + path);
    putMethod.addRequestHeader("Origin", URL);
    RequestEntity entity = new ByteArrayRequestEntity(body.getBytes("UTF-8"));
    putMethod.setRequestEntity(entity);
    if (userAndPasswordAreNotBlank(user, pwd)) {
      putMethod.setRequestHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    httpClient.executeMethod(putMethod);
    LOG.info("{} - {}", putMethod.getStatusCode(), putMethod.getStatusText());
    return putMethod;
  }

  protected static PostMethod httpPost(String path, String body) throws IOException {
    return httpPost(path, body, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  protected static PostMethod httpPost(String path, String request, String user, String pwd)
      throws IOException {
    LOG.info("Connecting to {}", URL + path);
    HttpClient httpClient = new HttpClient();
    PostMethod postMethod = new PostMethod(URL + path);
    postMethod.setRequestBody(request);
    postMethod.getParams().setCookiePolicy(CookiePolicy.IGNORE_COOKIES);
    if (userAndPasswordAreNotBlank(user, pwd)) {
      postMethod.setRequestHeader("Cookie", "JSESSIONID=" + getCookie(user, pwd));
    }
    httpClient.executeMethod(postMethod);
    LOG.info("{} - {}", postMethod.getStatusCode(), postMethod.getStatusText());
    return postMethod;
  }

  private static String getCookie(String user, String password) throws IOException {
    HttpClient httpClient = new HttpClient();
    PostMethod postMethod = new PostMethod(URL + "/login");
    postMethod.addRequestHeader("Origin", URL);
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

  /**
   * Status code matcher.
   */
  protected Matcher<? super HttpMethodBase> isForbidden() {
    return responsesWith(403);
  }

  protected Matcher<? super HttpMethodBase> isAllowed() {
    return responsesWith(200);
  }

  protected Matcher<? super HttpMethodBase> isCreated() {
    return responsesWith(201);
  }

  protected Matcher<? super HttpMethodBase> isBadRequest() {
    return responsesWith(400);
  }

  protected Matcher<? super HttpMethodBase> isNotFound() {
    return responsesWith(404);
  }

  protected Matcher<? super HttpMethodBase> isNotAllowed() {
    return responsesWith(405);
  }
}
