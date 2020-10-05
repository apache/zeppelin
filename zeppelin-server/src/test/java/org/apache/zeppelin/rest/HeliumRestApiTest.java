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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.internal.StringMap;
import com.google.gson.reflect.TypeToken;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.zeppelin.helium.Helium;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.helium.HeliumRegistry;
import org.apache.zeppelin.helium.HeliumType;

public class HeliumRestApiTest extends AbstractTestRestApi {
  private Gson gson = new Gson();
  private static Helium helium;

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp(HeliumRestApi.class.getSimpleName());
    helium = TestUtils.getInstance(Helium.class);
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Before
  public void setUp() throws IOException {
    HeliumTestRegistry registry = new HeliumTestRegistry("r1", "r1");
    helium.clear();

    registry.add(new HeliumPackage(
        HeliumType.APPLICATION,
        "name1",
        "desc1",
        "artifact1",
        "className1",
        new String[][]{},
        "",
        ""));

    registry.add(new HeliumPackage(
        HeliumType.APPLICATION,
        "name2",
        "desc2",
        "artifact2",
        "className2",
        new String[][]{},
        "",
        ""));

    helium.addRegistry(registry);
  }

  @After
  public void tearDown() {
    helium.clear();
  }

  @Test
  public void testGetAllPackageInfo() throws IOException {
    CloseableHttpResponse get = httpGet("/helium/package");
    assertThat(get, isAllowed());
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() { }.getType());
    Map<String, Set<String>> body = (Map<String, Set<String>>) resp.get("body");

    assertEquals(2, body.size());
    assertTrue(body.containsKey("name1"));
    assertTrue(body.containsKey("name2"));
    get.close();
  }

  @Test
  public void testGetAllEnabledPackageInfo() throws IOException {
    // No enabled packages initially
    CloseableHttpResponse get1 = httpGet("/helium/enabledPackage");
    assertThat(get1, isAllowed());
    Map<String, Object> resp1 = gson.fromJson(EntityUtils.toString(get1.getEntity(), StandardCharsets.UTF_8),
                new TypeToken<Map<String, Object>>() { }.getType());
    List<StringMap<Object>> body1 = (List<StringMap<Object>>) resp1.get("body");
    assertEquals(0, body1.size());

    // Enable "name1" package
    helium.enable("name1", "artifact1");

    CloseableHttpResponse get2 = httpGet("/helium/enabledPackage");
    assertThat(get2, isAllowed());
    Map<String, Object> resp2 = gson.fromJson(EntityUtils.toString(get2.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() { }.getType());
    List<StringMap<Object>> body2 = (List<StringMap<Object>>) resp2.get("body");

    assertEquals(1, body2.size());
    StringMap<Object> pkg = (StringMap<Object>) body2.get(0).get("pkg");
    assertEquals("name1", pkg.get("name"));
    get1.close();
    get2.close();
  }

  @Test
  public void testGetSinglePackageInfo() throws IOException {
    String packageName = "name1";
    CloseableHttpResponse get = httpGet("/helium/package/" + packageName);
    assertThat(get, isAllowed());
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() { }.getType());
    List<StringMap<Object>> body = (List<StringMap<Object>>) resp.get("body");

    assertEquals(1, body.size());
    StringMap<Object> pkg = (StringMap<Object>) body.get(0).get("pkg");
    assertEquals("name1", pkg.get("name"));
    get.close();
  }

  @Test
  public void testGetAllPackageConfigs() throws IOException {
    CloseableHttpResponse get = httpGet("/helium/config/");
    assertThat(get, isAllowed());
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() { }.getType());
    StringMap<Object> body = (StringMap<Object>) resp.get("body");
    // ToDo: Apply config with POST command and check update
    assertEquals(0, body.size());
    get.close();
  }

  @Test
  public void testGetPackageConfig() throws IOException {
    String packageName = "name1";
    String artifact = "artifact1";
    CloseableHttpResponse get = httpGet("/helium/config/" + packageName + "/" + artifact);
    assertThat(get, isAllowed());
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() { }.getType());
    StringMap<Object> body = (StringMap<Object>) resp.get("body");
    assertTrue(body.containsKey("confPersisted"));
    get.close();
  }

  @Test
  public void testEnableDisablePackage() throws IOException {
    String packageName = "name1";
    CloseableHttpResponse post1 = httpPost("/helium/enable/" + packageName, "");
    assertThat(post1, isAllowed());
    post1.close();

    CloseableHttpResponse get1 = httpGet("/helium/package/" + packageName);
    Map<String, Object> resp1 = gson.fromJson(EntityUtils.toString(get1.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() { }.getType());
    List<StringMap<Object>> body1 = (List<StringMap<Object>>) resp1.get("body");
    assertEquals(true, body1.get(0).get("enabled"));
    get1.close();

    CloseableHttpResponse post2 = httpPost("/helium/disable/" + packageName, "");
    assertThat(post2, isAllowed());
    post2.close();

    CloseableHttpResponse get2 = httpGet("/helium/package/" + packageName);
    Map<String, Object> resp2 = gson.fromJson(EntityUtils.toString(get2.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() { }.getType());
    List<StringMap<Object>> body2 = (List<StringMap<Object>>) resp2.get("body");
    assertEquals(false, body2.get(0).get("enabled"));
    get2.close();
  }

  @Test
  public void testVisualizationPackageOrder() throws IOException {
    CloseableHttpResponse get1 = httpGet("/helium/order/visualization");
    assertThat(get1, isAllowed());
    Map<String, Object> resp1 = gson.fromJson(EntityUtils.toString(get1.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() { }.getType());
    List<Object> body1 = (List<Object>) resp1.get("body");
    assertEquals(0, body1.size());
    get1.close();

    //We assume allPackages list has been refreshed before sorting
    helium.getAllPackageInfo();

    String postRequestJson = "[name2, name1]";
    CloseableHttpResponse post = httpPost("/helium/order/visualization", postRequestJson);
    assertThat(post, isAllowed());
    post.close();

    CloseableHttpResponse get2 = httpGet("/helium/order/visualization");
    assertThat(get2, isAllowed());
    Map<String, Object> resp2 = gson.fromJson(EntityUtils.toString(get2.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>() { }.getType());
    List<Object> body2 = (List<Object>) resp2.get("body");
    assertEquals(2, body2.size());
    assertEquals("name2", body2.get(0));
    assertEquals("name1", body2.get(1));
    get2.close();
  }
}

class HeliumTestRegistry extends HeliumRegistry {
  private List<HeliumPackage> infos = new LinkedList<>();

  HeliumTestRegistry(String name, String uri) {
    super(name, uri);
  }

  @Override
  public List<HeliumPackage> getAll() throws IOException {
    return infos;
  }

  public void add(HeliumPackage info) {
    infos.add(info);
  }
}
