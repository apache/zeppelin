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

import java.util.Properties;

import static org.junit.Assert.assertTrue;

public class ZeppCtxtVariableTest {

  public static class TestInterpreter extends Interpreter {

    TestInterpreter(Properties property) {
      super(property);
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {
      return null;
    }

    @Override
    public void cancel(InterpreterContext context) {
    }

    @Override
    public FormType getFormType() {
      return null;
    }

    @Override
    public int getProgress(InterpreterContext context) {
      return 0;
    }
  }

  private Interpreter interpreter;
  private ResourcePool resourcePool;

  @Before
  public void setUp() throws Exception {

    resourcePool = new LocalResourcePool("ZeppelinContextVariableInterpolationTest");

    InterpreterContext context = InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .setResourcePool(resourcePool)
        .build();
    InterpreterContext.set(context);

    interpreter = new TestInterpreter(new Properties());

    resourcePool.put("PI", "3.1415");

  }

  @After
  public void tearDown() throws Exception {
    InterpreterContext.remove();
  }

  @Test
  public void stringWithoutPatterns() {
    String result = interpreter.interpolate("The value of PI is not exactly 3.14", resourcePool);
    assertTrue("String without patterns", "The value of PI is not exactly 3.14".equals(result));
  }

  @Test
  public void substitutionInTheMiddle() {
    String result = interpreter.interpolate("The value of {{PI}} is {PI} now", resourcePool);
    assertTrue("Substitution in the middle", "The value of {PI} is 3.1415 now".equals(result));
  }

  @Test
  public void substitutionAtTheEnds() {
    String result = interpreter.interpolate("{{PI}} is now {PI}", resourcePool);
    assertTrue("Substitution at the ends", "{PI} is now 3.1415".equals(result));
  }

  @Test
  public void multiLineSubstitutionSuccessful1() {
    String result = interpreter.interpolate("{{PI}}\n{PI}\n{{PI}}\n{PI}", resourcePool);
    assertTrue("multiLineSubstitutionSuccessful1", "{PI}\n3.1415\n{PI}\n3.1415".equals(result));
  }


  @Test
  public void multiLineSubstitutionSuccessful2() {
    String result = interpreter.interpolate("prefix {PI} {{PI\n}} suffix", resourcePool);
    assertTrue("multiLineSubstitutionSuccessful2", "prefix 3.1415 {PI\n} suffix".equals(result));
  }


  @Test
  public void multiLineSubstitutionSuccessful3() {
    String result = interpreter.interpolate("prefix {{\nPI}} {PI} suffix", resourcePool);
    assertTrue("multiLineSubstitutionSuccessful3", "prefix {\nPI} 3.1415 suffix".equals(result));
  }


  @Test
  public void multiLineSubstitutionFailure2() {
    String result = interpreter.interpolate("prefix {PI\n} suffix", resourcePool);
    assertTrue("multiLineSubstitutionFailure2", "prefix {PI\n} suffix".equals(result));
  }


  @Test
  public void multiLineSubstitutionFailure3() {
    String result = interpreter.interpolate("prefix {\nPI} suffix", resourcePool);
    assertTrue("multiLineSubstitutionFailure3", "prefix {\nPI} suffix".equals(result));
  }

  @Test
  public void noUndefinedVariableError() {
    String result = interpreter.interpolate("This {pi} will pass silently", resourcePool);
    assertTrue("No partial substitution", "This {pi} will pass silently".equals(result));
  }

  @Test
  public void noPartialSubstitution() {
    String result = interpreter.interpolate("A {PI} and a {PIE} are different", resourcePool);
    assertTrue("No partial substitution", "A {PI} and a {PIE} are different".equals(result));
  }

  @Test
  public void substitutionAndEscapeMixed() {
    String result = interpreter.interpolate("A {PI} is not a {{PIE}}", resourcePool);
    assertTrue("Substitution and escape mixed", "A 3.1415 is not a {PIE}".equals(result));
  }

  @Test
  public void unbalancedBracesOne() {
    String result = interpreter.interpolate("A {PI} and a {{PIE} remain unchanged", resourcePool);
    assertTrue("Unbalanced braces - one", "A {PI} and a {{PIE} remain unchanged".equals(result));
  }

  @Test
  public void unbalancedBracesTwo() {
    String result = interpreter.interpolate("A {PI} and a {PIE}} remain unchanged", resourcePool);
    assertTrue("Unbalanced braces - one", "A {PI} and a {PIE}} remain unchanged".equals(result));
  }

  @Test
  public void tooManyBraces() {
    String result = interpreter.interpolate("This {{{PI}}} remain unchanged", resourcePool);
    assertTrue("Too many braces", "This {{{PI}}} remain unchanged".equals(result));
  }

  @Test
  public void randomBracesOne() {
    String result = interpreter.interpolate("A {{ starts an escaped sequence", resourcePool);
    assertTrue("Random braces - one", "A {{ starts an escaped sequence".equals(result));
  }

  @Test
  public void randomBracesTwo() {
    String result = interpreter.interpolate("A }} ends an escaped sequence", resourcePool);
    assertTrue("Random braces - two", "A }} ends an escaped sequence".equals(result));
  }

  @Test
  public void randomBracesThree() {
    String result = interpreter.interpolate("Paired { begin an escaped sequence", resourcePool);
    assertTrue("Random braces - three", "Paired { begin an escaped sequence".equals(result));
  }

  @Test
  public void randomBracesFour() {
    String result = interpreter.interpolate("Paired } end an escaped sequence", resourcePool);
    assertTrue("Random braces - four", "Paired } end an escaped sequence".equals(result));
  }

}
