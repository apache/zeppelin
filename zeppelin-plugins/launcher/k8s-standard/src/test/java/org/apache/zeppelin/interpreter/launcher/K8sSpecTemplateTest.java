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
package org.apache.zeppelin.interpreter.launcher;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class K8sSpecTemplateTest {
  @Test
  public void testRender() {
    // given template variables
    K8sSpecTemplate template = new K8sSpecTemplate();
    template.put("name", "world");

    // when
    String spec = template.render("Hello {{name}}");

    // then
    assertEquals("Hello world", spec);
  }

  @Test
  public void testObject() {
    K8sSpecTemplate template = new K8sSpecTemplate();
    template.put("k8s", ImmutableMap.of("key", "world"));

    // when
    String spec = template.render("Hello {{k8s.key}}");

    // then
    assertEquals("Hello world", spec);
  }

  @Test
  public void testRenderWithStrip() {
    // given
    K8sSpecTemplate template = new K8sSpecTemplate();
    template.put("test", "test");
      // when
    String spec = template.render(
          "  {% if test == \"test\" %}\n" +
          "  After commit\n" +
          "  {% endif %}\n");

    // then
    assertEquals("  After commit\n", spec);
  }

  @Test
  public void testIterate() {
    // given
    K8sSpecTemplate template = new K8sSpecTemplate();
    template.put("dict", ImmutableMap.of(
        "k1", "v1",
        "k2", "v2"
    ));

    // when
    String spec = template.render(
        "{% for key, value in dict.items() %}" +
            "key = {{key}}, value = {{value}}\n" +
            "{% endfor %}"
        );

    // then
    assertEquals(
        "key = k1, value = v1\n" +
        "key = k2, value = v2\n", spec);
  }

  @Test
  public void testLoadProperties() {
    // given
    K8sSpecTemplate template = new K8sSpecTemplate();
    Properties p = new Properties();
    p.put("k8s.intp.key1", "v1");
    p.put("k8s.intp.key2", "v2");
    p.put("k8s.key3", "v3");
    p.put("key4", "v4");

    // when
    template.loadProperties(p);

    // then
    assertEquals("v4", template.get("key4"));
    assertEquals("v3", ((Map) template.get("k8s")).get("key3"));
    assertEquals("v2", ((Map) ((Map) template.get("k8s")).get("intp")).get("key2"));
    assertEquals("v1", ((Map) ((Map) template.get("k8s")).get("intp")).get("key1"));
  }

  @Test
  public void testLoadPropertyOverrideString() {
    // given
    K8sSpecTemplate template = new K8sSpecTemplate();
    Properties p = new Properties();
    p.put("k8s", "v1");
    p.put("k8s.key1", "v2");

    // when
    template.loadProperties(p);

    // then
    assertEquals("v1", ((Map) template.get("k8s")).get("_"));
    assertEquals("v2", ((Map) template.get("k8s")).get("key1"));
  }

  @Test
  public void testLoadPropertyOverrideDict() {
    // given
    K8sSpecTemplate template = new K8sSpecTemplate();
    Properties p = new Properties();
    p.put("k8s.key1", "v2");
    p.put("k8s", "v1");

    // when
    template.loadProperties(p);

    // then
    assertEquals("v1", ((Map) template.get("k8s")).get("_"));
    assertEquals("v2", ((Map) template.get("k8s")).get("key1"));
  }

  @Test
  public void testLoadPropertyWithMap() {
    // given
    K8sSpecTemplate template = new K8sSpecTemplate();
    Properties p = new Properties();
    p.put("k8s", ImmutableMap.of("k1", "v1"));

    // when
    template.loadProperties(p);

    // then
    assertEquals("v1", ((Map) template.get("k8s")).get("k1"));
  }
}
