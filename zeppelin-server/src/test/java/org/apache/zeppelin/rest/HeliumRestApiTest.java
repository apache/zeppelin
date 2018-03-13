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

import com.google.gson.Gson;
import com.google.gson.internal.StringMap;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.zeppelin.helium.*;
import org.apache.zeppelin.server.ZeppelinServer;
import org.junit.*;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class HeliumRestApiTest extends AbstractTestRestApi {
    Gson gson = new Gson();

    @BeforeClass
    public static void init() throws Exception {
        AbstractTestRestApi.startUp(HeliumRestApi.class.getSimpleName());
    }

    @AfterClass
    public static void destroy() throws Exception {
        AbstractTestRestApi.shutDown();
    }

    @Before
    public void setUp() throws IOException {
        HeliumTestRegistry registry = new HeliumTestRegistry("r1", "r1");
        ZeppelinServer.helium.clear();
        ZeppelinServer.helium.addRegistry(registry);

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
    }

    @After
    public void tearDown() throws Exception {
        ZeppelinServer.helium.clear();
    }

    @Test
    public void testGetAllPackageInfo() throws IOException {
        GetMethod get = httpGet("/helium/package");
        assertThat(get, isAllowed());
        Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
                new TypeToken<Map<String, Object>>() { }.getType());
        Map<String, Set<String>> body = (Map<String, Set<String>>) resp.get("body");

        assertEquals(body.size(), 2);
        assertTrue(body.containsKey("name1"));
        assertTrue(body.containsKey("name2"));
    }

    @Test
    public void testGetAllEnabledPackageInfo() throws IOException {
        // No enabled packages initially
        GetMethod get1 = httpGet("/helium/enabledPackage");
        assertThat(get1, isAllowed());
        Map<String, Object> resp1 = gson.fromJson(get1.getResponseBodyAsString(),
                new TypeToken<Map<String, Object>>() { }.getType());
        List<StringMap<Object>> body1 = (List<StringMap<Object>>) resp1.get("body");
        assertEquals(body1.size(), 0);

        // Enable "name1" package
        ZeppelinServer.helium.enable("name1","artifact1");

        GetMethod get2 = httpGet("/helium/enabledPackage");
        assertThat(get2, isAllowed());
        Map<String, Object> resp2 = gson.fromJson(get2.getResponseBodyAsString(),
                new TypeToken<Map<String, Object>>() { }.getType());
        List<StringMap<Object>> body2 = (List<StringMap<Object>>) resp2.get("body");

        assertEquals(body2.size(), 1);
        StringMap<Object> pkg = (StringMap<Object>) body2.get(0).get("pkg");
        assertEquals(pkg.get("name"), "name1");
    }

    @Test
    public void testGetSinglePackageInfo() throws IOException {
        String packageName = "name1";
        GetMethod get = httpGet("/helium/package/" + packageName);
        assertThat(get, isAllowed());
        Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
                new TypeToken<Map<String, Object>>() { }.getType());
        List<StringMap<Object>> body = (List<StringMap<Object>>) resp.get("body");

        assertEquals(body.size(), 1);
        StringMap<Object> pkg = (StringMap<Object>) body.get(0).get("pkg");
        assertEquals(pkg.get("name"), "name1");
    }

    @Test
    public void testGetAllPackageConfigs() throws IOException {
        GetMethod get = httpGet("/helium/config/");
        assertThat(get, isAllowed());
        Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
                new TypeToken<Map<String, Object>>() { }.getType());
        StringMap<Object> body = (StringMap<Object>) resp.get("body");
        // ToDo: Apply config with POST command and check update
        assertEquals(body.size(), 0);
    }

    @Test
    public void testGetPackageConfig() throws IOException {
        String packageName = "name1";
        String artifact = "artifact1";
        GetMethod get = httpGet("/helium/config/" + packageName + "/" + artifact);
        assertThat(get, isAllowed());
        Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
                new TypeToken<Map<String, Object>>() { }.getType());
        StringMap<Object> body = (StringMap<Object>) resp.get("body");
        assertTrue(body.containsKey("confPersisted"));
    }

    @Test
    public void testEnableDisablePackage() throws IOException {
        String packageName = "name1";
        PostMethod post1 = httpPost("/helium/enable/" + packageName, "");
        assertThat(post1, isAllowed());
        post1.releaseConnection();

        GetMethod get1 = httpGet("/helium/package/" + packageName);
        Map<String, Object> resp1 = gson.fromJson(get1.getResponseBodyAsString(),
                new TypeToken<Map<String, Object>>() { }.getType());
        List<StringMap<Object>> body1 = (List<StringMap<Object>>) resp1.get("body");
        assertEquals(body1.get(0).get("enabled"), true);

        PostMethod post2 = httpPost("/helium/disable/" + packageName, "");
        assertThat(post2, isAllowed());
        post2.releaseConnection();

        GetMethod get2 = httpGet("/helium/package/" + packageName);
        Map<String, Object> resp2 = gson.fromJson(get2.getResponseBodyAsString(),
                new TypeToken<Map<String, Object>>() { }.getType());
        List<StringMap<Object>> body2 = (List<StringMap<Object>>) resp2.get("body");
        assertEquals(body2.get(0).get("enabled"), false);
    }

    @Test
    public void testVisualizationPackageOrder() throws IOException {
        GetMethod get1 = httpGet("/helium/order/visualization");
        assertThat(get1, isAllowed());
        Map<String, Object> resp1 = gson.fromJson(get1.getResponseBodyAsString(),
                new TypeToken<Map<String, Object>>() { }.getType());
        List<Object> body1 = (List<Object>) resp1.get("body");
        assertEquals(body1.size(), 0);

        //We assume allPackages list has been refreshed before sorting
        ZeppelinServer.helium.getAllPackageInfo();

        String postRequestJson = "[name2, name1]";
        PostMethod post = httpPost("/helium/order/visualization", postRequestJson);
        assertThat(post, isAllowed());
        post.releaseConnection();

        GetMethod get2 = httpGet("/helium/order/visualization");
        assertThat(get2, isAllowed());
        Map<String, Object> resp2 = gson.fromJson(get2.getResponseBodyAsString(),
                new TypeToken<Map<String, Object>>() { }.getType());
        List<Object> body2 = (List<Object>) resp2.get("body");
        assertEquals(body2.size(), 2);
        assertEquals(body2.get(0), "name2");
        assertEquals(body2.get(1), "name1");
    }
}

class HeliumTestRegistry extends HeliumRegistry {
    private List<HeliumPackage> infos = new LinkedList<>();

    public HeliumTestRegistry(String name, String uri) {
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
