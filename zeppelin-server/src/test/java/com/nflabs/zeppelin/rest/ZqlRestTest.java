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

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ZqlRestTest extends RestApiTestAbstract {

  @BeforeClass
  public static void init() throws Exception {
    RestApiTestAbstract.startUp();
  }

  @AfterClass
  public static void destroy() {
    RestApiTestAbstract.shutDown();
  }

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
    Map<String, Object> tmp = (Map<String, Object>) jobs.get(0);
    return tmp.get("id").toString();
  }

  @Test
  public void test002_getZqlSessionId() throws IOException {
    // when

    GetMethod httpGetRoot = httpGet("/zql/" + getFisrtId());
    // then
    assertThat(httpGetRoot, isAllowed());
    String body = httpGetRoot.getResponseBodyAsString();
    assertThat(body, isValidJSON());
  }

  @Test
  public void test003_getZqlHistorySessionId() throws IOException {
    // when

    GetMethod httpGetRoot = httpGet("/zql/history/" + getFisrtId());
    // then
    assertThat(httpGetRoot, isAllowed());
    String body = httpGetRoot.getResponseBodyAsString();
    assertThat(body, isValidJSON());
  }

}
