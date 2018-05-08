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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.remote.RemoteEventClientWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BaseZeppelinContext.class})
@PowerMockIgnore({"javax.net.ssl.*","javax.security.*"})
public class SparkShimsTest {

  @Test
  @SuppressWarnings("unchecked")
  public void buildSparkJobUrlTest() throws IOException {
    HttpClient mockHttpClient = mock(HttpClient.class);
    HttpResponse mockHttpResponse = mock(HttpResponse.class);
    StatusLine mockStatusLine = mock(StatusLine.class);

    PowerMockito.mockStatic(BaseZeppelinContext.class);
    RemoteEventClientWrapper mockRemoteEventClientWrapper = mock(RemoteEventClientWrapper.class);

    Properties mockProperties = mock(Properties.class);

    when(mockHttpClient.execute(Matchers.<HttpUriRequest>any())).thenReturn(mockHttpResponse);
    when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
    when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);

    when(BaseZeppelinContext.getEventClient()).thenReturn(mockRemoteEventClientWrapper);
    ArgumentCaptor<Map<String, String>> mapArgumentCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>)(Class)Map.class);
    doNothing()
        .when(mockRemoteEventClientWrapper)
        .onParaInfosReceived(anyString(), anyString(), mapArgumentCaptor.capture());

    when(mockProperties.getProperty("spark.jobGroup.id")).thenReturn("job-note-paragraph");

    SparkShims sparkShims;
    try {
      sparkShims = SparkShims.getInstance(SparkVersion.SPARK_2_0_0.toString());
    } catch (Exception e) {
      sparkShims = SparkShims.getInstance(SparkVersion.SPARK_1_6_0.toString());
    }
    sparkShims.setHttpClient(mockHttpClient);

    sparkShims.buildSparkJobUrl("http://sparkurl", 0, mockProperties);

    Map<String, String> mapValue = mapArgumentCaptor.getValue();
    assertTrue(mapValue.keySet().contains("jobUrl"));
    assertTrue(mapValue.get("jobUrl").contains("/jobs/job?id="));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void buildSparkJobUrlWithoutJobIdTest() throws IOException {
    HttpClient mockHttpClient = mock(HttpClient.class);
    HttpResponse mockHttpResponse = mock(HttpResponse.class);
    StatusLine mockStatusLine = mock(StatusLine.class);

    PowerMockito.mockStatic(BaseZeppelinContext.class);
    RemoteEventClientWrapper mockRemoteEventClientWrapper = mock(RemoteEventClientWrapper.class);

    Properties mockProperties = mock(Properties.class);

    when(mockHttpClient.execute(Matchers.<HttpUriRequest>any())).thenReturn(mockHttpResponse);
    when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
    when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_NOT_FOUND);

    when(BaseZeppelinContext.getEventClient()).thenReturn(mockRemoteEventClientWrapper);
    ArgumentCaptor<Map<String, String>> mapArgumentCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>)(Class)Map.class);
    doNothing()
        .when(mockRemoteEventClientWrapper)
        .onParaInfosReceived(anyString(), anyString(), mapArgumentCaptor.capture());

    when(mockProperties.getProperty("spark.jobGroup.id")).thenReturn("job-note-paragraph");

    SparkShims sparkShims;
    try {
      sparkShims = SparkShims.getInstance(SparkVersion.SPARK_2_0_0.toString());
    } catch (Exception e) {
      sparkShims = SparkShims.getInstance(SparkVersion.SPARK_1_6_0.toString());
    }
    sparkShims.setHttpClient(mockHttpClient);

    sparkShims.buildSparkJobUrl("http://sparkurl", 0, mockProperties);

    Map<String, String> mapValue = mapArgumentCaptor.getValue();
    assertTrue(mapValue.keySet().contains("jobUrl"));
    assertFalse(mapValue.get("jobUrl").contains("/jobs/job?id="));
    assertTrue(mapValue.get("jobUrl").contains("/jobs"));
  }
}
