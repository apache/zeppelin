package com.nflabs.zeppelin.rest;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.nflabs.zeppelin.server.ZeppelinServer;

public abstract class AbstractTestRestApi {

  protected static final Logger LOG = LoggerFactory.getLogger(AbstractTestRestApi.class);

  static final String restApiUrl = "/api";
  static final String url = getUrlToTest();
  protected static final boolean wasRunning = checkIfServerIsRuning();

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

  static ExecutorService executor = Executors.newSingleThreadExecutor();
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
    }
  }

  protected static void shutDown() {
    if (!wasRunning) {
      LOG.info("Terminating test Zeppelin...");
      executor.shutdown();
      try {
        executor.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
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
    httpClient.executeMethod(getMethod);
    LOG.info("{} - {}", getMethod.getStatusCode(), getMethod.getStatusText());
    return getMethod;
  }

  protected static PostMethod httpPost(String path, String body) throws IOException {
    LOG.info("Connecting to {}", url + path);
    HttpClient httpClient = new HttpClient();
    PostMethod postMethod = new PostMethod(url + path);
    RequestEntity entity = new ByteArrayRequestEntity(body.getBytes("UTF-8"));
    postMethod.setRequestEntity(entity);
    httpClient.executeMethod(postMethod);
    LOG.info("{} - {}", postMethod.getStatusCode(), postMethod.getStatusText());
    return postMethod;
  }

  protected Matcher<GetMethod> responsesWith(final int expectedStatusCode) {
    return new TypeSafeMatcher<GetMethod>() {
      WeakReference<GetMethod> method;

      @Override
      public boolean matchesSafely(GetMethod getMethod) {
        method = (method == null) ? new WeakReference<GetMethod>(getMethod) : method;
        return getMethod.getStatusCode() == expectedStatusCode;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("HTTP response ").appendValue(expectedStatusCode)
            .appendText(" from ").appendText(method.get().getPath());
      }

      @Override
      protected void describeMismatchSafely(GetMethod item, Description description) {
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
  protected Matcher<? super GetMethod> isForbiden() {
    return responsesWith(403);
  }

  protected Matcher<? super GetMethod> isAllowed() {
    return responsesWith(200);
  }

  protected Matcher<? super GetMethod> isNotAllowed() {
    return responsesWith(405);
  }

}
