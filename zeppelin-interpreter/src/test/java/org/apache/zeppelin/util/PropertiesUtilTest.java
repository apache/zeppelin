/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class PropertiesUtilTest {

  private static final String KEY = "property";

  @Test
  void missingPropertyReturnsDefault() {
    Properties properties = new Properties();

    assertEquals("default", PropertiesUtil.getString(properties, KEY, "default"));
    assertEquals(42L, PropertiesUtil.getLong(properties, KEY, 42L));
    assertEquals(42, PropertiesUtil.getInt(properties, KEY, 42));
  }

  @Test
  void getStringReturnsEmptyAndWhitespaceValuesUnchanged() {
    Properties properties = new Properties();

    properties.setProperty(KEY, "");
    assertEquals("", PropertiesUtil.getString(properties, KEY, "default"));

    properties.setProperty(KEY, "  ");
    assertEquals("  ", PropertiesUtil.getString(properties, KEY, "default"));
  }

  @Test
  void getLongParsesValidValues() {
    Properties properties = new Properties();

    properties.setProperty(KEY, "42");
    assertEquals(42L, PropertiesUtil.getLong(properties, KEY, 0L));

    properties.setProperty(KEY, "-42");
    assertEquals(-42L, PropertiesUtil.getLong(properties, KEY, 0L));

    properties.setProperty(KEY, Long.toString(Long.MAX_VALUE));
    assertEquals(Long.MAX_VALUE, PropertiesUtil.getLong(properties, KEY, 0L));

    properties.setProperty(KEY, Long.toString(Long.MIN_VALUE));
    assertEquals(Long.MIN_VALUE, PropertiesUtil.getLong(properties, KEY, 0L));
  }

  @Test
  void getLongReturnsDefaultForUnparseableValues() {
    Properties properties = new Properties();

    properties.setProperty(KEY, "");
    assertEquals(42L, PropertiesUtil.getLong(properties, KEY, 42L));

    properties.setProperty(KEY, "not-a-long");
    assertEquals(42L, PropertiesUtil.getLong(properties, KEY, 42L));

    properties.setProperty(KEY, " 42 ");
    assertEquals(42L, PropertiesUtil.getLong(properties, KEY, 42L));
  }

  @Test
  void getIntParsesValidValues() {
    Properties properties = new Properties();

    properties.setProperty(KEY, "42");
    assertEquals(42, PropertiesUtil.getInt(properties, KEY, 0));

    properties.setProperty(KEY, "-42");
    assertEquals(-42, PropertiesUtil.getInt(properties, KEY, 0));

    properties.setProperty(KEY, Integer.toString(Integer.MAX_VALUE));
    assertEquals(Integer.MAX_VALUE, PropertiesUtil.getInt(properties, KEY, 0));

    properties.setProperty(KEY, Integer.toString(Integer.MIN_VALUE));
    assertEquals(Integer.MIN_VALUE, PropertiesUtil.getInt(properties, KEY, 0));
  }

  @Test
  void getIntReturnsDefaultForUnparseableValues() {
    Properties properties = new Properties();

    properties.setProperty(KEY, "");
    assertEquals(42, PropertiesUtil.getInt(properties, KEY, 42));

    properties.setProperty(KEY, "not-an-int");
    assertEquals(42, PropertiesUtil.getInt(properties, KEY, 42));

    properties.setProperty(KEY, Long.toString((long) Integer.MAX_VALUE + 1));
    assertEquals(42, PropertiesUtil.getInt(properties, KEY, 42));

    properties.setProperty(KEY, " 42 ");
    assertEquals(42, PropertiesUtil.getInt(properties, KEY, 42));
  }
}
