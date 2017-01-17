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

export const DefaultDisplayType = {
  ELEMENT: 'ELEMENT',
  TABLE: 'TABLE',
  HTML: 'HTML',
  ANGULAR: 'ANGULAR',
  TEXT: 'TEXT',
};

export class FrontendInterpreterResult {
  constructor(resultDisplayType, resultDataGenerator) {
    /**
     * backend interpreter uses `data` and `type` as field names.
     * let's use the same field to keep consistency.
     */
    this.type = resultDisplayType;
    this.data = resultDataGenerator;
  }

  static isFunctionGenerator(generator) {
    return (generator && typeof generator === 'function');
  }

  static isPromiseGenerator(generator) {
    return (generator && typeof generator.then === 'function');
  }

  static isObjectGenerator(generator) {
    return (
      !FrontendInterpreterResult.isFunctionGenerator(generator) &&
      !FrontendInterpreterResult.isPromiseGenerator(generator));
  }

  /**
   * @returns {string} display type for this frontend interpreter result.
   */
  getType() {
    return this.type;
  }

  /**
   * generator (`data`) can be promise, function or just object
   * - if data is an object, it will be used directly.
   * - if data is a function, it will be called with DOM element id
   *   where the final output is rendered.
   * - if data is a promise, the post processing logic
   *   will be called in `then()` of this promise.
   * @returns {*} `data` which can be object, function or promise.
   */
  getData() {
    return this.data;
  }
}
