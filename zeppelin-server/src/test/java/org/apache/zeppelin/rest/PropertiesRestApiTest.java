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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class PropertiesRestApiTest extends AbstractTestRestApi {
  protected static final Logger LOG = LoggerFactory.getLogger(PropertiesRestApiTest.class);
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
  public void testInvalidRequest() throws IOException {
    String jsonInvalidRequestNameNull = "{\"name\" : null, \"value\" : \"value\"}";
    String jsonInvalidRequestValueNull = "{\"name\" : \"test\", \"value\" : null}";
    String jsonInvalidRequestAllNull = "{\"name\" : null, \"value\" : null}";

    PutMethod nameNullPut = httpPut("/property", jsonInvalidRequestNameNull);
    nameNullPut.addRequestHeader("Origin", "http://localhost");
    assertThat(nameNullPut, isBadRequest());
    nameNullPut.releaseConnection();

    PutMethod passwordNullPut = httpPut("/property", jsonInvalidRequestValueNull);
    passwordNullPut.addRequestHeader("Origin", "http://localhost");
    assertThat(passwordNullPut, isBadRequest());
    passwordNullPut.releaseConnection();

    PutMethod allNullPut = httpPut("/property", jsonInvalidRequestAllNull);
    allNullPut.addRequestHeader("Origin", "http://localhost");
    assertThat(allNullPut, isBadRequest());
    allNullPut.releaseConnection();
  }

  public Map<String, String> testGetProperties() throws IOException {
    GetMethod getMethod = httpGet("/property");
    getMethod.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> resp = gson.fromJson(getMethod.getResponseBodyAsString(),
            new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> properties = (Map<String, String>) resp.get("body");
    getMethod.releaseConnection();
    return properties;
  }

  public void testPutProperties(String requestData) throws IOException {
    PutMethod putMethod = httpPut("/property", requestData);
    putMethod.addRequestHeader("Origin", "http://localhost");
    assertThat(putMethod, isAllowed());
    putMethod.releaseConnection();
  }

  public void testRemoveProperties() throws IOException {
    DeleteMethod deleteMethod = httpDelete("/property/");
    assertThat("Test delete method:", deleteMethod, isAllowed());
    deleteMethod.releaseConnection();
  }

  public void testRemoveProperty(String name) throws IOException {
    DeleteMethod deleteMethod = httpDelete("/property/" + name);
    assertThat("Test delete method:", deleteMethod, isAllowed());
    deleteMethod.releaseConnection();
  }

  @Test
  public void testPropertyAPIs() throws IOException {
    String requestData1 = "{\"name\" : \"property1\", \"value\" : \"value1\"}";
    String property = "property1";
    Map<String, String> properties;

    testPutProperties(requestData1);
    properties = testGetProperties();
    assertNotNull("CredentialMap should not be null", testGetProperties());

    testRemoveProperty(property);
    properties = testGetProperties();
    assertNull("property should be null", properties.get("property1"));
    
    testPutProperties(requestData1);
    properties = testGetProperties();
    assertNotNull("CredentialMap should not be null", testGetProperties());

    testRemoveProperties();
    properties = testGetProperties();
    assertEquals("Compare properties", properties.toString(), "{}");
  }
}
