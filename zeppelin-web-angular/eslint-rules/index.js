/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Local ESLint plugin for the project's Angular-specific constructor parameter
// ordering rule, reimplemented from the former custom TSLint rule
// (ZEPPELIN-6301 / ZEPPELIN-6372). The alphabetical public-api.ts export sort
// (ZEPPELIN-6325) is delegated to eslint-plugin-perfectionist instead.

'use strict';

module.exports = {
  rules: {
    'constructor-params-order': require('./constructor-params-order')
  }
};
