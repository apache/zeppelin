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

package org.apache.zeppelin.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.resource.ResourcePool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZeppCtxtVariableTest {

  private ResourcePool resourcePool;

  @BeforeEach
  void setUp() throws Exception {
    resourcePool = new LocalResourcePool("ZeppelinContextVariableInterpolationTest");
    resourcePool.put("PI", "3.1415");
  }

  @Test
  void stringWithoutPatterns() {
    String result = AbstractInterpreter.interpolate("The value of PI is not exactly 3.14", resourcePool);
    assertEquals("The value of PI is not exactly 3.14", result);
  }

  @Test
  void substitutionInTheMiddle() {
    String result = AbstractInterpreter.interpolate("The value of {{PI}} is {PI} now", resourcePool);
    assertEquals("The value of {PI} is 3.1415 now", result);
  }

  @Test
  void substitutionAtTheEnds() {
    String result = AbstractInterpreter.interpolate("{{PI}} is now {PI}", resourcePool);
    assertEquals("{PI} is now 3.1415", result);
  }

  @Test
  void multiLineSubstitutionSuccessful1() {
    String result = AbstractInterpreter.interpolate("{{PI}}\n{PI}\n{{PI}}\n{PI}", resourcePool);
    assertEquals("{PI}\n3.1415\n{PI}\n3.1415", result);
  }


  @Test
  void multiLineSubstitutionSuccessful2() {
    String result = AbstractInterpreter.interpolate("prefix {PI} {{PI\n}} suffix", resourcePool);
    assertEquals("prefix 3.1415 {PI\n} suffix", result);
  }


  @Test
  void multiLineSubstitutionSuccessful3() {
    String result = AbstractInterpreter.interpolate("prefix {{\nPI}} {PI} suffix", resourcePool);
    assertEquals("prefix {\nPI} 3.1415 suffix", result);
  }


  @Test
  void multiLineSubstitutionFailure2() {
    String result = AbstractInterpreter.interpolate("prefix {PI\n} suffix", resourcePool);
    assertEquals("prefix {PI\n} suffix", result);
  }


  @Test
  void multiLineSubstitutionFailure3() {
    String result = AbstractInterpreter.interpolate("prefix {\nPI} suffix", resourcePool);
    assertEquals("prefix {\nPI} suffix", result);
  }

  @Test
  void noUndefinedVariableError() {
    String result = AbstractInterpreter.interpolate("This {pi} will pass silently", resourcePool);
    assertEquals("This {pi} will pass silently", result);
  }

  @Test
  void noPartialSubstitution() {
    String result = AbstractInterpreter.interpolate("A {PI} and a {PIE} are different", resourcePool);
    assertEquals("A {PI} and a {PIE} are different", result);
  }

  @Test
  void substitutionAndEscapeMixed() {
    String result = AbstractInterpreter.interpolate("A {PI} is not a {{PIE}}", resourcePool);
    assertEquals("A 3.1415 is not a {PIE}", result);
  }

  @Test
  void unbalancedBracesOne() {
    String result = AbstractInterpreter.interpolate("A {PI} and a {{PIE} remain unchanged", resourcePool);
    assertEquals("A {PI} and a {{PIE} remain unchanged", result);
  }

  @Test
  void unbalancedBracesTwo() {
    String result = AbstractInterpreter.interpolate("A {PI} and a {PIE}} remain unchanged", resourcePool);
    assertEquals("A {PI} and a {PIE}} remain unchanged", result);
  }

  @Test
  void tooManyBraces() {
    String result = AbstractInterpreter.interpolate("This {{{PI}}} remain unchanged", resourcePool);
    assertEquals("This {{{PI}}} remain unchanged", result);
  }

  @Test
  void randomBracesOne() {
    String result = AbstractInterpreter.interpolate("A {{ starts an escaped sequence", resourcePool);
    assertEquals("A {{ starts an escaped sequence", result);
  }

  @Test
  void randomBracesTwo() {
    String result = AbstractInterpreter.interpolate("A }} ends an escaped sequence", resourcePool);
    assertEquals("A }} ends an escaped sequence", result);
  }

  @Test
  void randomBracesThree() {
    String result = AbstractInterpreter.interpolate("Paired { begin an escaped sequence", resourcePool);
    assertEquals("Paired { begin an escaped sequence", result);
  }

  @Test
  void randomBracesFour() {
    String result = AbstractInterpreter.interpolate("Paired } end an escaped sequence", resourcePool);
    assertEquals("Paired } end an escaped sequence", result);
  }

}
