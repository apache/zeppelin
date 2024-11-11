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
package org.apache.zeppelin.rest.message;

import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test class for the UpdateInterpreterSettingRequest.
 * This class tests the behavior of UpdateInterpreterSettingRequest methods,
 * especially focusing on property type handling and value conversions.
 */
class UpdateInterpreterSettingRequestTest {

  private Map<String, InterpreterProperty> properties;
  private List<Dependency> dependencies;
  private InterpreterOption option;
  private UpdateInterpreterSettingRequest request;

  /**
   * Setup method that initializes test fixtures before each test.
   * Mocks dependencies and option objects to isolate UpdateInterpreterSettingRequest behavior.
   */
  @BeforeEach
  void setUp() {
    properties = new HashMap<>();
    dependencies = Collections.emptyList();
    option = mock(InterpreterOption.class);
    request = new UpdateInterpreterSettingRequest(properties, dependencies, option);
  }

  /**
   * Tests getProperties method to verify that properties with "number" type
   * and whole-number Double values are correctly converted to Integer.
   * Verifies that only whole-number Doubles are converted and non-integer Doubles remain unchanged.
   */
  @Test
  void testGetPropertiesWithWholeNumberDoubleConversion() {
    InterpreterProperty property1 = mock(InterpreterProperty.class);
    when(property1.getType()).thenReturn("number");
    when(property1.getValue()).thenReturn(5.0);

    InterpreterProperty property2 = mock(InterpreterProperty.class);
    when(property2.getType()).thenReturn("number");
    when(property2.getValue()).thenReturn(5.5);

    properties.put("property1", property1);
    properties.put("property2", property2);

    Map<String, InterpreterProperty> resultProperties = request.getProperties();

    verify(property1).setValue(5);
    verify(property2, never()).setValue(any());
    assertEquals(properties, resultProperties);
  }

  /**
   * Tests getProperties method when the property type is not "number".
   * Verifies that no conversion is performed on non-number types.
   */
  @Test
  void testGetPropertiesWithoutConversion() {
    InterpreterProperty property = mock(InterpreterProperty.class);
    when(property.getType()).thenReturn("string");
    when(property.getValue()).thenReturn("test");

    properties.put("property", property);

    Map<String, InterpreterProperty> resultProperties = request.getProperties();

    verify(property, never()).setValue(any());
    assertEquals(properties, resultProperties);
  }

  /**
   * Tests getDependencies method to confirm that it returns the correct dependencies list.
   */
  @Test
  void testGetDependencies() {
    assertEquals(dependencies, request.getDependencies());
  }

  /**
   * Tests getOption method to confirm that it returns the correct interpreter option.
   */
  @Test
  void testGetOption() {
    assertEquals(option, request.getOption());
  }
}
