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
}
