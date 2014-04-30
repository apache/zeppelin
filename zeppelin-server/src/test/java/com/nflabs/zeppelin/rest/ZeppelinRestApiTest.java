package com.nflabs.zeppelin.rest;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
/**
 * BASIC Zeppelin rest api tests
 * TODO: Add Post,Put,Delete test and method
 *
 * @author anthonycorbacho
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ZeppelinRestApiTest extends AbstractTestRestApi {

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp();
  }

  @AfterClass
  public static void destroy() {
    AbstractTestRestApi.shutDown();
  }

  /***
   * ROOT API TEST
   ***/
  @Test
  public void getApiRoot() throws IOException {
    // when
    GetMethod httpGetRoot = httpGet("/");
    // then
    assertThat(httpGetRoot, isAllowed());
    httpGetRoot.releaseConnection();
  }

  /***
   * TEST ZAN REST API
   ***/

  @Test
  public void getZanRoot() throws IOException {
    // when
    GetMethod httpGetRoot = httpGet("/zan");
    // then
    assertThat(httpGetRoot, isAllowed());
    httpGetRoot.releaseConnection();
  }

  @Test
  public void getZanRunning() throws IOException {
    // when
    GetMethod httpGetRoot = httpGet("/zan/running");
    // then
    assertThat(httpGetRoot, isAllowed());
    httpGetRoot.releaseConnection();
  }

  /***
   * TEST ZQL REST API
   ***/

  private static Map<String, Object> data = new HashMap<String, Object>();

  @Test
  public void test001_getZqlRoot() throws IOException {
    // when
    GetMethod httpGetRoot = httpGet("/zql");
    // then
    assertThat(httpGetRoot, isAllowed());
    String body = httpGetRoot.getResponseBodyAsString();
    assertThat(body, isValidJSON());
    JsonElement json = new JsonParser().parse(body);
    Gson gson = new Gson();
    data = (Map<String, Object>) gson.fromJson(json, data.getClass());
    assertFalse("JSON object is null", json.isJsonNull());
    httpGetRoot.releaseConnection();
  }

  private String getFisrtId() {
    List<Object> jobs = (List<Object>) data.get("body");
    if (jobs.isEmpty())
      return "";
    Map<String, Object> tmp = (Map<String, Object>) jobs.get(0);
    if (tmp.isEmpty())
      return "";
    return tmp.get("id").toString();
  }

  @Test
  public void test002_getZqlSessionId() throws IOException {
    // when
    String id = getFisrtId();
    // TODO (anthony): Implement post method and create test to get at least one element
    if (id.isEmpty()) {
      /** no job */
      assertTrue(true);
      return;
    }
    GetMethod httpGetRoot = httpGet("/zql/" + id);
    // then
    assertThat(httpGetRoot, isAllowed());
    String body = httpGetRoot.getResponseBodyAsString();
    assertThat(body, isValidJSON());
  }

  @Test
  public void test003_getZqlHistorySessionId() throws IOException {
    // when
    String id = getFisrtId();
    // TODO (anthony): Implement post method and create test to get at least one element
    if (id.isEmpty()) {
      /** no job */
      assertTrue(true);
      return;
    }
    GetMethod httpGetRoot = httpGet("/zql/history/" + id);
    // then
    assertThat(httpGetRoot, isAllowed());
    String body = httpGetRoot.getResponseBodyAsString();
    assertThat(body, isValidJSON());
  }

}
