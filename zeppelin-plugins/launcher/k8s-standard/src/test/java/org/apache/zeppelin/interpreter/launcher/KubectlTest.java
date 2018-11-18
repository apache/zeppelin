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

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KubectlTest {

  @Test(expected = IOException.class)
  public void testKubeclCommandNotExists() throws IOException {
    // given
    Kubectl kubectl = new Kubectl("invalidcommand");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    // when
    kubectl.execute(new String[] {}, null, stdout, stderr);

    // then throw IOException
  }

  @Test
  public void testStdout() throws IOException {
    // given
    Kubectl kubectl = new Kubectl("echo");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    // when
    kubectl.execute(new String[] {"hello"}, null, stdout, stderr);

    // then
    assertEquals("hello\n", stdout.toString());
    assertEquals("", stderr.toString());
  }

  @Test
  public void testStderr() throws IOException {
    // given
    Kubectl kubectl = new Kubectl("sh");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    // when
    try {
      kubectl.execute(new String[]{"-c", "yoyo"}, null, stdout, stderr);
    } catch (IOException e) {
    }

    // then
    assertEquals("", stdout.toString());
    assertTrue(0 < stderr.toString().length());
  }

  @Test
  public void testStdin() throws IOException {
    // given
    Kubectl kubectl = new Kubectl("wc");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    InputStream stdin = IOUtils.toInputStream("Hello");

    // when
    kubectl.execute(new String[]{"-c"}, stdin, stdout, stderr);

    // then
    assertEquals("5", stdout.toString().trim());
    assertEquals("", stderr.toString());
  }

  @Test
  public void testExecSpecAndGetJson() throws IOException {
    // given
    Kubectl kubectl = new Kubectl("cat");
    String spec = "{'k1': 'v1', 'k2': 2}";

    // when
    Map<String, Object> result = kubectl.execAndGetJson(new String[]{}, spec);

    // then
    assertEquals("v1", result.get("k1"));
    assertEquals(2.0, result.get("k2"));
  }

  @Test(expected = com.google.gson.JsonSyntaxException.class)
  public void testExecSpecAndGetJsonInvalidOutput() throws IOException {
    // given
    Kubectl kubectl = new Kubectl("cat");
    String spec = "Not a json format";

    // when
    Map<String, Object> result = kubectl.execAndGetJson(new String[]{}, spec);

    // then throw JsonSyntaxException
  }
}
