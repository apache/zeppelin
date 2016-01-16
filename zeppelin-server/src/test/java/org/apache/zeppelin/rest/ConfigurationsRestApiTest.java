package org.apache.zeppelin.rest;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class ConfigurationsRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp();
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  public void testGetAll() throws IOException {
    GetMethod get = httpGet("/configurations/all");
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
        new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> body = (Map<String, String>) resp.get("body");
    assertTrue(body.size() > 0);
    // it shouldn't have key/value pair which key contains "password"
    assertTrue(Iterators.all(body.keySet().iterator(), new Predicate<String>() {
        @Override
        public boolean apply(String key) {
          return !key.contains("password");
        }
      }
    ));
  }

  @Test
  public void testGetViaPrefix() throws IOException {
    final String prefix = "zeppelin.server";
    GetMethod get = httpGet("/configurations/prefix/" + prefix);
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
        new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> body = (Map<String, String>) resp.get("body");
    assertTrue(body.size() > 0);
    assertTrue(Iterators.all(body.keySet().iterator(), new Predicate<String>() {
          @Override
          public boolean apply(String key) {
            return !key.contains("password") && key.startsWith(prefix);
          }
        }
    ));
  }
}
