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
package org.apache.zeppelin.display;

import org.apache.zeppelin.interpreter.InterpreterContext;

/**
 * The DisplayFunction interface
 *
 * Any interpreter that wan to use zeppelinContext.display(...)
 * should register at least one implementation of this class
 * in the zeppelinContext
 */
public interface DisplayFunction {

  /**
   * Whether this display function can handle the give
   * object
   * @param anyObject target object
   * @return true/false
   */
  boolean canDisplay(Object anyObject);

  /**
   * Generate the display output for the given input object
   * <strong>Warning: this method should not be invoked
   *  without invoking canDisplay(...)
   * </strong>
   *
   * @param anyObject input object
   * @param displayParams display parameters
   * @return the formatted display
   */
  void display(Object anyObject, DisplayParams displayParams);

}
