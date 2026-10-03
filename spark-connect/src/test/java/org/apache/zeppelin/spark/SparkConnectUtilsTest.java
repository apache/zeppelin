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

package org.apache.zeppelin.spark;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SparkConnectUtilsTest {

  @Test
  void testBuildConnectionStringDefault() {
    Properties props = new Properties();
    String result = SparkConnectUtils.buildConnectionString(props);
    assertEquals("sc://localhost:15002", result);
  }

  @Test
  void testBuildConnectionStringCustomRemote() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://spark-server.example.com:15002");
    String result = SparkConnectUtils.buildConnectionString(props);
    assertEquals("sc://spark-server.example.com:15002", result);
  }

  @Test
  void testBuildConnectionStringWithToken() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://spark-server.example.com:15002");
    props.setProperty("spark.connect.token", "my-secret-token");
    String result = SparkConnectUtils.buildConnectionString(props);
    assertEquals("sc://spark-server.example.com:15002/;token=my-secret-token", result);
  }

  @Test
  void testBuildConnectionStringWithTokenAndExistingParams() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://host:15002/;use_ssl=true");
    props.setProperty("spark.connect.token", "tok123");
    String result = SparkConnectUtils.buildConnectionString(props);
    assertEquals("sc://host:15002/;use_ssl=true;token=tok123", result);
  }

  @Test
  void testBuildConnectionStringEmptyToken() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://host:15002");
    props.setProperty("spark.connect.token", "");
    String result = SparkConnectUtils.buildConnectionString(props);
    assertEquals("sc://host:15002", result);
  }

  @Test
  void testBuildConnectionStringWithUseSsl() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://ranking-cluster-m:8080");
    props.setProperty("spark.connect.use_ssl", "true");
    String result = SparkConnectUtils.buildConnectionString(props);
    assertEquals("sc://ranking-cluster-m:8080/;use_ssl=true", result);
  }

  @Test
  void testBuildConnectionStringWithSslAndToken() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://cluster:8080");
    props.setProperty("spark.connect.use_ssl", "true");
    props.setProperty("spark.connect.token", "abc123");
    String result = SparkConnectUtils.buildConnectionString(props);
    assertEquals("sc://cluster:8080/;token=abc123;use_ssl=true", result);
  }

  @Test
  void testBuildConnectionStringDataprocTunnel() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://localhost:15002");
    String result = SparkConnectUtils.buildConnectionString(props);
    assertEquals("sc://localhost:15002", result);
  }

  @Test
  void testBuildConnectionStringDataprocDirect() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://ranking-cluster-m:8080");
    String result = SparkConnectUtils.buildConnectionString(props);
    assertEquals("sc://ranking-cluster-m:8080", result);
  }

  @Test
  void testBuildConnectionStringWithUserName() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://cluster:8080");
    String result = SparkConnectUtils.buildConnectionString(props, "alice");
    assertEquals("sc://cluster:8080/;user_id=alice", result);
  }

  @Test
  void testBuildConnectionStringWithUserNameAndToken() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://cluster:8080");
    props.setProperty("spark.connect.token", "tok");
    String result = SparkConnectUtils.buildConnectionString(props, "bob");
    assertEquals("sc://cluster:8080/;token=tok;user_id=bob", result);
  }

  @Test
  void testBuildConnectionStringUserIdAlreadyInUrl() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://cluster:8080/;user_id=preexisting");
    String result = SparkConnectUtils.buildConnectionString(props, "alice");
    assertEquals("sc://cluster:8080/;user_id=preexisting", result);
  }

  @Test
  void testBuildConnectionStringNullUserName() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://cluster:8080");
    String result = SparkConnectUtils.buildConnectionString(props, null);
    assertEquals("sc://cluster:8080", result);
  }

  @Test
  void testBuildConnectionStringBlankUserName() {
    Properties props = new Properties();
    props.setProperty("spark.remote", "sc://cluster:8080");
    String result = SparkConnectUtils.buildConnectionString(props, "   ");
    assertEquals("sc://cluster:8080", result);
  }

  @Test
  void testReplaceReservedChars() {
    assertEquals("hello world", SparkConnectUtils.replaceReservedChars("hello\tworld"));
    assertEquals("hello world", SparkConnectUtils.replaceReservedChars("hello\nworld"));
    assertEquals("null", SparkConnectUtils.replaceReservedChars(null));
    assertEquals("normal", SparkConnectUtils.replaceReservedChars("normal"));
    assertEquals("a b c", SparkConnectUtils.replaceReservedChars("a\tb\nc"));
  }
}
