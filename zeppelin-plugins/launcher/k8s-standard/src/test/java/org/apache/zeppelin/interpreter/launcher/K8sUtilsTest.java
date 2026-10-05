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

package org.apache.zeppelin.interpreter.launcher;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

class K8sUtilsTest {

  @Test
  void testConvert() {
    assertEquals("484Mi", K8sUtils.calculateMemoryWithDefaultOverhead("100m"));
    assertEquals("1408Mi", K8sUtils.calculateMemoryWithDefaultOverhead("1Gb"));
    assertEquals("4505Mi", K8sUtils.calculateMemoryWithDefaultOverhead("4Gb"));
    assertEquals("6758Mi", K8sUtils.calculateMemoryWithDefaultOverhead("6Gb"));
    assertEquals("9011Mi", K8sUtils.calculateMemoryWithDefaultOverhead("8Gb"));
    // some extrem values
    assertEquals("112640Mi", K8sUtils.calculateMemoryWithDefaultOverhead("100Gb"));
    assertEquals("115343360Mi", K8sUtils.calculateMemoryWithDefaultOverhead("100Tb"));
  }

  @Test
  void testExceptionMaxLong() {
    assertThrows(NumberFormatException.class, () ->
      K8sUtils.calculateMemoryWithDefaultOverhead("10000000Tb")
    );

  }

  @Test
  void testExceptionNoValidNumber() {
    assertThrows(NumberFormatException.class, () ->
      K8sUtils.calculateMemoryWithDefaultOverhead("NoValidNumber10000000Tb")
    );
  }

  @Test
  void testGenerateK8sName() {
    assertEquals("zeppelin", K8sUtils.generateK8sName("", false));
    assertEquals("zeppelin", K8sUtils.generateK8sName(null, false));
    assertEquals("test", K8sUtils.generateK8sName("test", false));
    assertEquals("test", K8sUtils.generateK8sName("!test", false));
    assertEquals("zeppelin", K8sUtils.generateK8sName("!", false));
    assertEquals("test", K8sUtils.generateK8sName(".test", false));
    assertEquals("test", K8sUtils.generateK8sName("...test", false));
    assertEquals("test", K8sUtils.generateK8sName(".test.", false));
    assertEquals("test", K8sUtils.generateK8sName("...test....", false));
    assertEquals("test", K8sUtils.generateK8sName("Test", false));

    assertEquals(253 - "zeppelin".length() , K8sUtils.generateK8sName(RandomStringUtils.randomAlphabetic(260), true).length());
  }

  @Test
  void testGenerateK8sLabelValue() {
    assertEquals("firstnamelastname", K8sUtils.generateK8sLabelValue("Firstname Lastname"));
    assertEquals("alicecorp.com", K8sUtils.generateK8sLabelValue("Alice@Corp.com"));
    assertEquals("test", K8sUtils.generateK8sLabelValue("  test  "));
    // leading and trailing non alphanumeric characters are dropped
    assertEquals("test", K8sUtils.generateK8sLabelValue("-.test.-"));
    assertEquals("a-b", K8sUtils.generateK8sLabelValue("-a-b-"));
    // nothing usable left
    assertEquals("", K8sUtils.generateK8sLabelValue(""));
    assertEquals("", K8sUtils.generateK8sLabelValue("   "));
    assertEquals("", K8sUtils.generateK8sLabelValue("!@#"));
    assertEquals("", K8sUtils.generateK8sLabelValue(null));
  }

  @Test
  void testGenerateK8sLabelValueTruncatesTo63() {
    String longValue = RandomStringUtils.randomAlphabetic(100);
    String result = K8sUtils.generateK8sLabelValue(longValue);
    assertEquals(63, result.length());
    assertEquals(longValue.toLowerCase().substring(0, 63), result);

    // a separator at the cut position must not remain at the end
    String cutOnSeparator = RandomStringUtils.randomAlphabetic(62).toLowerCase() + "-tail";
    assertEquals(cutOnSeparator.substring(0, 62), K8sUtils.generateK8sLabelValue(cutOnSeparator));
  }
}
