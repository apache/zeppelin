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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.hadoop.util.VersionInfo;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class SparkShimsTest {

  @ParameterizedTest
  @CsvSource({"2.6.0, false",
  "2.6.1, false",
  "2.6.2, false",
  "2.6.3, false",
  "2.6.4, false",
  "2.6.5, false",
  "2.6.6, true", // The latest fixed version
  "2.6.7, true", // Future version
  "2.7.0, false",
  "2.7.1, false",
  "2.7.2, false",
  "2.7.3, false",
  "2.7.4, true", // The latest fixed version
  "2.7.5, true", // Future versions
  "2.8.0, false",
  "2.8.1, false",
  "2.8.2, true", // The latest fixed version
  "2.8.3, true", // Future versions
  "2.9.0, true", // The latest fixed version
  "2.9.1, true", // Future versions
  "3.0.0, true", // The latest fixed version
  "3.0.0-alpha4, true", // The latest fixed version
  "3.0.1, true"}) // Future versions
  void checkYarnVersionTest(String version, boolean expected) {
      SparkShims sparkShims =
          new SparkShims(new Properties()) {
            @Override
            public void setupSparkListener(String master,
                                           String sparkWebUrl,
                                           InterpreterContext context) {}

            @Override
            public String showDataFrame(Object obj, int maxResult, InterpreterContext context) {
              return null;
            }

            @Override
            public Object getAsDataFrame(String value) {
              return null;
            }
          };
      assertEquals(expected, sparkShims.supportYarn6615(version));
  }

  @Nested
  class SingleTests {
    SparkShims sparkShims;
    InterpreterContext mockContext;
    RemoteInterpreterEventClient mockIntpEventClient;

    @BeforeEach
    public void setUp() {
      mockContext = mock(InterpreterContext.class);
      mockIntpEventClient = mock(RemoteInterpreterEventClient.class);
      when(mockContext.getIntpEventClient()).thenReturn(mockIntpEventClient);

      try {
        sparkShims = SparkShims.getInstance(SparkVersion.SPARK_3_2_0.toString(), new Properties(), null);
      } catch (Throwable e1) {
        throw new RuntimeException("All SparkShims are tried, but no one can be created.");
      }
    }

    @Test
    void runUnderLocalTest() {
      Properties properties = new Properties();
      properties.setProperty("spark.jobGroup.id", "zeppelin|user1|noteId|paragraphId");
      sparkShims.buildSparkJobUrl("local", "http://sparkurl", 0, properties, mockContext);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> argument = ArgumentCaptor.forClass(HashMap.class);
      verify(mockIntpEventClient).onParaInfosReceived(argument.capture());
      Map<String, String> mapValue = argument.getValue();
      assertTrue(mapValue.keySet().contains("jobUrl"));
      assertTrue(mapValue.get("jobUrl").contains("/jobs/job?id="));
    }

    @Test
    void runUnderYarnTest() {
      Properties properties = new Properties();
      properties.setProperty("spark.jobGroup.id", "zeppelin|user1|noteId|paragraphId");
      sparkShims.buildSparkJobUrl("yarn", "http://sparkurl", 0, properties, mockContext);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> argument = ArgumentCaptor.forClass(HashMap.class);
      verify(mockIntpEventClient).onParaInfosReceived(argument.capture());
      Map<String, String> mapValue = argument.getValue();
      assertTrue(mapValue.keySet().contains("jobUrl"));

      if (sparkShims.supportYarn6615(VersionInfo.getVersion())) {
        assertTrue(mapValue.get("jobUrl").contains("/jobs/job?id="));
      } else {
        assertFalse(mapValue.get("jobUrl").contains("/jobs/job?id="));
      }
    }
  }
}
