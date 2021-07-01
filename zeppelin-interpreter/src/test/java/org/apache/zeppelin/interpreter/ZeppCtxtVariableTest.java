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

import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.resource.ResourcePool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ZeppCtxtVariableTest {

  private ResourcePool resourcePool;

  @Before
  public void setUp() throws Exception {
    resourcePool = new LocalResourcePool("ZeppelinContextVariableInterpolationTest");
    resourcePool.put("PI", "3.1415");
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void stringWithoutPatterns() {
    String result = AbstractInterpreter.interpolate("The value of PI is not exactly 3.14", resourcePool);
    assertEquals("String without patterns", "The value of PI is not exactly 3.14", result);
  }

  @Test
  public void substitutionInTheMiddle() {
    String result = AbstractInterpreter.interpolate("The value of {{PI}} is {PI} now", resourcePool);
    assertEquals("Substitution in the middle", "The value of {PI} is 3.1415 now", result);
  }

  @Test
  public void substitutionAtTheEnds() {
    String result = AbstractInterpreter.interpolate("{{PI}} is now {PI}", resourcePool);
    assertEquals("Substitution at the ends", "{PI} is now 3.1415", result);
  }

  @Test
  public void multiLineSubstitutionSuccessful1() {
    String result = AbstractInterpreter.interpolate("{{PI}}\n{PI}\n{{PI}}\n{PI}", resourcePool);
    assertEquals("multiLineSubstitutionSuccessful1", "{PI}\n3.1415\n{PI}\n3.1415", result);
  }


  @Test
  public void multiLineSubstitutionSuccessful2() {
    String result = AbstractInterpreter.interpolate("prefix {PI} {{PI\n}} suffix", resourcePool);
    assertEquals("multiLineSubstitutionSuccessful2", "prefix 3.1415 {PI\n} suffix", result);
  }


  @Test
  public void multiLineSubstitutionSuccessful3() {
    String result = AbstractInterpreter.interpolate("prefix {{\nPI}} {PI} suffix", resourcePool);
    assertEquals("multiLineSubstitutionSuccessful3", "prefix {\nPI} 3.1415 suffix", result);
  }


  @Test
  public void multiLineSubstitutionFailure2() {
    String result = AbstractInterpreter.interpolate("prefix {PI\n} suffix", resourcePool);
    assertEquals("multiLineSubstitutionFailure2", "prefix {PI\n} suffix", result);
  }


  @Test
  public void multiLineSubstitutionFailure3() {
    String result = AbstractInterpreter.interpolate("prefix {\nPI} suffix", resourcePool);
    assertEquals("multiLineSubstitutionFailure3", "prefix {\nPI} suffix", result);
  }

  @Test
  public void noUndefinedVariableError() {
    String result = AbstractInterpreter.interpolate("This {pi} will pass silently", resourcePool);
    assertEquals("No partial substitution", "This {pi} will pass silently", result);
  }

  @Test
  public void noPartialSubstitution() {
    String result = AbstractInterpreter.interpolate("A {PI} and a {PIE} are different", resourcePool);
    assertEquals("No partial substitution", "A {PI} and a {PIE} are different", result);
  }

  @Test
  public void substitutionAndEscapeMixed() {
    String result = AbstractInterpreter.interpolate("A {PI} is not a {{PIE}}", resourcePool);
    assertEquals("Substitution and escape mixed", "A 3.1415 is not a {PIE}", result);
  }

  @Test
  public void unbalancedBracesOne() {
    String result = AbstractInterpreter.interpolate("A {PI} and a {{PIE} remain unchanged", resourcePool);
    assertEquals("Unbalanced braces - one", "A {PI} and a {{PIE} remain unchanged", result);
  }

  @Test
  public void unbalancedBracesTwo() {
    String result = AbstractInterpreter.interpolate("A {PI} and a {PIE}} remain unchanged", resourcePool);
    assertEquals("Unbalanced braces - one", "A {PI} and a {PIE}} remain unchanged", result);
  }

  @Test
  public void tooManyBraces() {
    String result = AbstractInterpreter.interpolate("This {{{PI}}} remain unchanged", resourcePool);
    assertEquals("Too many braces", "This {{{PI}}} remain unchanged", result);
  }

  @Test
  public void randomBracesOne() {
    String result = AbstractInterpreter.interpolate("A {{ starts an escaped sequence", resourcePool);
    assertEquals("Random braces - one", "A {{ starts an escaped sequence", result);
  }

  @Test
  public void randomBracesTwo() {
    String result = AbstractInterpreter.interpolate("A }} ends an escaped sequence", resourcePool);
    assertEquals("Random braces - two", "A }} ends an escaped sequence", result);
  }

  @Test
  public void randomBracesThree() {
    String result = AbstractInterpreter.interpolate("Paired { begin an escaped sequence", resourcePool);
    assertEquals("Random braces - three", "Paired { begin an escaped sequence", result);
  }

  @Test
  public void randomBracesFour() {
    String result = AbstractInterpreter.interpolate("Paired } end an escaped sequence", resourcePool);
    assertEquals("Random braces - four", "Paired } end an escaped sequence", result);
  }

}
