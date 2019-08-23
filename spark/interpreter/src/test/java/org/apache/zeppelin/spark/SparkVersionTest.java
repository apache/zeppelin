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
    assertEquals(99999, SparkVersion.fromVersionString("DEV-10.10").toNumber());
  }

  @Test
  public void testUnsupportedVersion() {
    assertTrue(SparkVersion.fromVersionString("1.4.2").isUnsupportedVersion());
    assertFalse(SparkVersion.fromVersionString("2.3.0").isUnsupportedVersion());
    assertTrue(SparkVersion.fromVersionString("0.9.0").isUnsupportedVersion());
    assertTrue(SparkVersion.UNSUPPORTED_FUTURE_VERSION.isUnsupportedVersion());
    // should support spark2 version of HDP 2.5
    assertFalse(SparkVersion.fromVersionString("2.0.0.2.5.0.0-1245").isUnsupportedVersion());
  }

  @Test
  public void testSparkVersion() {
    // test equals
    assertEquals(SparkVersion.SPARK_2_0_0, SparkVersion.fromVersionString("2.0.0"));
    assertEquals(SparkVersion.SPARK_2_0_0, SparkVersion.fromVersionString("2.0.0-SNAPSHOT"));
    // test spark2 version of HDP 2.5
    assertEquals(SparkVersion.SPARK_2_0_0, SparkVersion.fromVersionString("2.0.0.2.5.0.0-1245"));

    // test newer than
    assertTrue(SparkVersion.SPARK_2_3_0.newerThan(SparkVersion.SPARK_2_0_0));
    assertTrue(SparkVersion.SPARK_2_3_0.newerThanEquals(SparkVersion.SPARK_2_3_0));
    assertFalse(SparkVersion.SPARK_2_0_0.newerThan(SparkVersion.SPARK_2_3_0));

    // test older than
    assertTrue(SparkVersion.SPARK_2_0_0.olderThan(SparkVersion.SPARK_2_3_0));
    assertTrue(SparkVersion.SPARK_2_0_0.olderThanEquals(SparkVersion.SPARK_2_0_0));
    assertFalse(SparkVersion.SPARK_2_3_0.olderThan(SparkVersion.SPARK_2_0_0));

    // test newerThanEqualsPatchVersion
    assertTrue(SparkVersion.fromVersionString("2.3.1")
            .newerThanEqualsPatchVersion(SparkVersion.fromVersionString("2.3.0")));
    assertFalse(SparkVersion.fromVersionString("2.3.1")
            .newerThanEqualsPatchVersion(SparkVersion.fromVersionString("2.3.2")));
    assertFalse(SparkVersion.fromVersionString("2.3.1")
            .newerThanEqualsPatchVersion(SparkVersion.fromVersionString("2.2.0")));

    // conversion
    assertEquals(20300, SparkVersion.SPARK_2_3_0.toNumber());
    assertEquals("2.3.0", SparkVersion.SPARK_2_3_0.toString());
  }
}
