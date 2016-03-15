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

import static org.junit.Assert.*;

import org.junit.Test;

public class SparkVersionTest {

  @Test
  public void testUnknownSparkVersion() {
    assertEquals(999, SparkVersion.fromVersionString("DEV-10.10").toNumber());
  }

  @Test
  public void testUnsupportedVersion() {
    assertTrue(SparkVersion.fromVersionString("9.9.9").isUnsupportedVersion());
    assertFalse(SparkVersion.fromVersionString("1.5.9").isUnsupportedVersion());
    assertTrue(SparkVersion.fromVersionString("0.9.0").isUnsupportedVersion());
    assertTrue(SparkVersion.UNSUPPORTED_FUTURE_VERSION.isUnsupportedVersion());
  }

  @Test
  public void testSparkVersion() {
    // test equals
    assertEquals(SparkVersion.SPARK_1_2_0, SparkVersion.fromVersionString("1.2.0"));
    assertEquals(SparkVersion.SPARK_1_5_0, SparkVersion.fromVersionString("1.5.0-SNAPSHOT"));

    // test newer than
    assertFalse(SparkVersion.SPARK_1_2_0.newerThan(SparkVersion.SPARK_1_2_0));
    assertFalse(SparkVersion.SPARK_1_2_0.newerThan(SparkVersion.SPARK_1_3_0));
    assertTrue(SparkVersion.SPARK_1_2_0.newerThan(SparkVersion.SPARK_1_1_0));

    assertTrue(SparkVersion.SPARK_1_2_0.newerThanEquals(SparkVersion.SPARK_1_2_0));
    assertFalse(SparkVersion.SPARK_1_2_0.newerThanEquals(SparkVersion.SPARK_1_3_0));
    assertTrue(SparkVersion.SPARK_1_2_0.newerThanEquals(SparkVersion.SPARK_1_1_0));

    // test older than
    assertFalse(SparkVersion.SPARK_1_2_0.olderThan(SparkVersion.SPARK_1_2_0));
    assertFalse(SparkVersion.SPARK_1_2_0.olderThan(SparkVersion.SPARK_1_1_0));
    assertTrue(SparkVersion.SPARK_1_2_0.olderThan(SparkVersion.SPARK_1_3_0));

    assertTrue(SparkVersion.SPARK_1_2_0.olderThanEquals(SparkVersion.SPARK_1_2_0));
    assertFalse(SparkVersion.SPARK_1_2_0.olderThanEquals(SparkVersion.SPARK_1_1_0));
    assertTrue(SparkVersion.SPARK_1_2_0.olderThanEquals(SparkVersion.SPARK_1_3_0));

    // conversion
    assertEquals(120, SparkVersion.SPARK_1_2_0.toNumber());
    assertEquals("1.2.0", SparkVersion.SPARK_1_2_0.toString());
  }
}
