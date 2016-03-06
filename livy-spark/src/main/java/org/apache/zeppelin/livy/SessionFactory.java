package org.apache.zeppelin.livy;

import static org.apache.zeppelin.livy.Http.delete;
import static org.apache.zeppelin.livy.Http.get;
import static org.apache.zeppelin.livy.Http.post;

import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.ning.http.client.Response;

/**
 * 
 *
 */
public class SessionFactory {

  public SessionFactory() {
  }

  public static Session createSession(String host, String kind,
    Properties property) throws IOException {
    HashMap<String, String> conf = new HashMap<String, String>();
    Gson gson = new Gson();
    
    String appName = property.getProperty("spark.app.name");

    conf.put("spark.driver.cores", property.getProperty("spark.driver.cores"));
    conf.put("spark.executor.cores", property.getProperty("spark.executor.cores"));
    conf.put("spark.driver.memory", property.getProperty("spark.driver.memory"));
    conf.put("spark.executor.memory", property.getProperty("spark.executor.memory"));

    if (!property.getProperty("spark.dynamicAllocation.enabled").equals("true")) {
      conf.put("spark.executor.instances", property.getProperty("spark.executor.instances"));
    }

    if (property.getProperty("spark.dynamicAllocation.enabled").equals("true")) {
      conf.put("spark.dynamicAllocation.enabled",
          property.getProperty("spark.dynamicAllocation.enabled"));
      conf.put("spark.shuffle.service.enabled", "true");
      conf.put("spark.dynamicAllocation.cachedExecutorIdleTimeout",
          property.getProperty("spark.dynamicAllocation.cachedExecutorIdleTimeout"));
      conf.put("spark.dynamicAllocation.minExecutors",
          property.getProperty("spark.dynamicAllocation.minExecutors"));
      conf.put("spark.dynamicAllocation.initialExecutors",
          property.getProperty("spark.dynamicAllocation.initialExecutors"));
      conf.put("spark.dynamicAllocation.maxExecutors",
          property.getProperty("spark.dynamicAllocation.maxExecutors"));
    }

    String confData = gson.toJson(conf);
    String data = "{\"kind\": \"" + kind + "\", \"name\": \"" +
      appName + "\", \"conf\": " + confData
        + "}";
    
    Response r = post(host + "/sessions", data);
    String json = r.getResponseBody();
    Session session = gson.fromJson(json, Session.class);
    session.url = host + "/sessions/" + session.id;
    
    Callable<Session> callableTask = new SessionCallable(session);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<Session> future = executor.submit(callableTask);
    
    int maxWaitTime = 180000;
    int curentWaitTime = 0;
    // Waiting 3 minutes for session creation otherwise kill
    while (!future.isDone()) {
      try {
        TimeUnit.MILLISECONDS.sleep(2000);
        curentWaitTime += 2000;
        if (curentWaitTime == maxWaitTime) {
          future.cancel(true);
          executor.shutdown();
          SessionFactory.deleteSession(session);
          return null;
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    }
    try {
      session = future.get();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }

    executor.shutdown();

    return session;
  }

  public static Session getSession(Session session) throws IOException {
    String url = session.url;
    Response r = get(url);
    if (r.getStatusCode() >= 300) {
      return null;
    }
    String json = r.getResponseBody();
    Gson gson = new Gson();
    session = gson.fromJson(json, Session.class);
    session.url = url;

    return session;
  }

  public static void deleteSession(Session session) {
    delete(session.url);

  }

}
