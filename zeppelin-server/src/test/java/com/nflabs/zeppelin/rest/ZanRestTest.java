package com.nflabs.zeppelin.rest;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ZanRestTest extends RestApiTestAbstract {

  @BeforeClass
  public static void init() throws Exception {
    RestApiTestAbstract.startUp();
  }

  @AfterClass
  public static void destroy() {
    RestApiTestAbstract.shutDown();
  }

  @Test
  public void test001_getZanRoot() throws IOException {
    // when
    GetMethod httpGetRoot = httpGet("/zan");
    // then
    assertThat(httpGetRoot, isAllowed());
    httpGetRoot.releaseConnection();
  }

  @Test
  public void test002_getZanRunning() throws IOException {
    // when
    GetMethod httpGetRoot = httpGet("/zan/running");
    // then
    assertThat(httpGetRoot, isAllowed());
    httpGetRoot.releaseConnection();
  }

}
