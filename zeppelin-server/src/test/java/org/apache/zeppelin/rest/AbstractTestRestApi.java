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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.server.ZeppelinServer;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public abstract class AbstractTestRestApi {

  protected static final Logger LOG = LoggerFactory.getLogger(AbstractTestRestApi.class);

  static final String restApiUrl = "/api";
  static final String url = getUrlToTest();
  protected static final boolean wasRunning = checkIfServerIsRuning();
  static boolean pySpark = false;

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
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }
  };

  protected static void startUp() throws Exception {
    if (!wasRunning) {
      LOG.info("Staring test Zeppelin up...");
      executor = Executors.newSingleThreadExecutor();
      executor.submit(server);
      long s = System.currentTimeMillis();
      boolean started = false;
      while (System.currentTimeMillis() - s < 1000 * 60 * 3) {  // 3 minutes
        Thread.sleep(2000);
        started = checkIfServerIsRuning();
        if (started == true) {
          break;
        }
      }
      if (started == false) {
        throw new RuntimeException("Can not start Zeppelin server");
      }
      LOG.info("Test Zeppelin stared.");


      // ci environment runs spark cluster for testing
      // so configure zeppelin use spark cluster
      if ("true".equals(System.getenv("CI"))) {
        // assume first one is spark
        InterpreterSetting sparkIntpSetting = null;
        for(InterpreterSetting intpSetting : ZeppelinServer.notebook.getInterpreterFactory().get()) {
          if (intpSetting.getGroup().equals("spark")) {
            sparkIntpSetting = intpSetting;
          }
        }

        // set spark master
        sparkIntpSetting.getProperties().setProperty("master", "spark://" + getHostname() + ":7071");

        // set spark home for pyspark
        sparkIntpSetting.getProperties().setProperty("spark.home", getSparkHome());
        pySpark = true;

        ZeppelinServer.notebook.getInterpreterFactory().restart(sparkIntpSetting.id());
      } else {
        // assume first one is spark
        InterpreterSetting sparkIntpSetting = null;
        for(InterpreterSetting intpSetting : ZeppelinServer.notebook.getInterpreterFactory().get()) {
          if (intpSetting.getGroup().equals("spark")) {
            sparkIntpSetting = intpSetting;
          }
        }

        String sparkHome = getSparkHome();
        if (sparkHome != null) {
          // set spark home for pyspark
          sparkIntpSetting.getProperties().setProperty("spark.home", sparkHome);
          pySpark = true;
        }

        ZeppelinServer.notebook.getInterpreterFactory().restart(sparkIntpSetting.id());
      }
    }
  }

  private static String getHostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      e.printStackTrace();
      return "localhost";
    }
  }

  private static String getSparkHome() {
    String sparkHome = getSparkHomeRecursively(new File(System.getProperty("user.dir")));
    System.out.println("SPARK HOME detected " + sparkHome);
    return sparkHome;
  }

  boolean isPyspark() {
    return pySpark;
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
    if (dir.getName().matches("spark-[0-9\\.]+-bin-hadoop[0-9\\.]+")) {
      File pidDir = new File(dir, "run");
      if (pidDir.isDirectory() && pidDir.listFiles().length > 0) {
        return true;
      }
    }
    return false;
  }

  protected static void shutDown() throws Exception {
    if (!wasRunning) {
      // restart interpreter to stop all interpreter processes
      List<String> settingList = ZeppelinServer.notebook.getInterpreterFactory()
          .getDefaultInterpreterSettingList();
      for (String setting : settingList) {
        ZeppelinServer.notebook.getInterpreterFactory().restart(setting);
      }

      LOG.info("Terminating test Zeppelin...");
      ZeppelinServer.jettyWebServer.stop();
      executor.shutdown();

      long s = System.currentTimeMillis();
      boolean started = true;
      while (System.currentTimeMillis() - s < 1000 * 60 * 3) {  // 3 minutes
        Thread.sleep(2000);
        started = checkIfServerIsRuning();
        if (started == false) {
          break;
        }
      }
      if (started == true) {
        throw new RuntimeException("Can not stop Zeppelin server");
      }

      LOG.info("Test Zeppelin terminated.");
    }
  }

  protected static boolean checkIfServerIsRuning() {
    GetMethod request = null;
    boolean isRunning = true;
    try {
      request = httpGet("/");
      isRunning = request.getStatusCode() == 200;
    } catch (IOException e) {
      isRunning = false;
    } finally {
      if (request != null) {
        request.releaseConnection();
      }
    }
    return isRunning;
  }

  protected static GetMethod httpGet(String path) throws IOException {
    LOG.info("Connecting to {}", url + path);
    HttpClient httpClient = new HttpClient();
    GetMethod getMethod = new GetMethod(url + path);
    getMethod.addRequestHeader("Origin", url);
    httpClient.executeMethod(getMethod);
    LOG.info("{} - {}", getMethod.getStatusCode(), getMethod.getStatusText());
    return getMethod;
  }

  protected static DeleteMethod httpDelete(String path) throws IOException {
    LOG.info("Connecting to {}", url + path);
    HttpClient httpClient = new HttpClient();
    DeleteMethod deleteMethod = new DeleteMethod(url + path);
    deleteMethod.addRequestHeader("Origin", url);
    httpClient.executeMethod(deleteMethod);
    LOG.info("{} - {}", deleteMethod.getStatusCode(), deleteMethod.getStatusText());
    return deleteMethod;
  }

  protected static PostMethod httpPost(String path, String body) throws IOException {
    LOG.info("Connecting to {}", url + path);
    HttpClient httpClient = new HttpClient();
    PostMethod postMethod = new PostMethod(url + path);
    postMethod.addRequestHeader("Origin", url);
    RequestEntity entity = new ByteArrayRequestEntity(body.getBytes("UTF-8"));
    postMethod.setRequestEntity(entity);
    httpClient.executeMethod(postMethod);
    LOG.info("{} - {}", postMethod.getStatusCode(), postMethod.getStatusText());
    return postMethod;
  }

  protected static PutMethod httpPut(String path, String body) throws IOException {
    LOG.info("Connecting to {}", url + path);
    HttpClient httpClient = new HttpClient();
    PutMethod putMethod = new PutMethod(url + path);
    putMethod.addRequestHeader("Origin", url);
    RequestEntity entity = new ByteArrayRequestEntity(body.getBytes("UTF-8"));
    putMethod.setRequestEntity(entity);
    httpClient.executeMethod(putMethod);
    LOG.info("{} - {}", putMethod.getStatusCode(), putMethod.getStatusText());
    return putMethod;
  }

  protected Matcher<HttpMethodBase> responsesWith(final int expectedStatusCode) {
    return new TypeSafeMatcher<HttpMethodBase>() {
      WeakReference<HttpMethodBase> method;

      @Override
      public boolean matchesSafely(HttpMethodBase httpMethodBase) {
        method = (method == null) ? new WeakReference<HttpMethodBase>(httpMethodBase) : method;
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

  //Create new Setting and return Setting ID
  protected String createTempSetting(String tempName) throws IOException {

    InterpreterGroup interpreterGroup =  ZeppelinServer.notebook.getInterpreterFactory().add(tempName,"newGroup",
        new InterpreterOption(false),new Properties());
    return interpreterGroup.getId();
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

  /** Status code matcher */
  protected Matcher<? super HttpMethodBase> isForbiden() { return responsesWith(403); }

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
