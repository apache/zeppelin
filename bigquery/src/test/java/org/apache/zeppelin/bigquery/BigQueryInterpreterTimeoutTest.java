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

package org.apache.zeppelin.bigquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

class BigQueryInterpreterTimeoutTest {

  @Test
  void testSetTimeoutAppliesClientTimeoutsAndDelegates() throws Exception {
    AtomicBoolean delegated = new AtomicBoolean(false);
    HttpRequestInitializer delegate = request -> delegated.set(true);
    HttpRequest request = new NetHttpTransport().createRequestFactory()
        .buildGetRequest(new GenericUrl("https://example.com"));

    BigQueryInterpreter.setTimeout(delegate).initialize(request);

    assertTrue(delegated.get());
    assertEquals(200000, request.getConnectTimeout());
    assertEquals(200000, request.getReadTimeout());
  }
}
