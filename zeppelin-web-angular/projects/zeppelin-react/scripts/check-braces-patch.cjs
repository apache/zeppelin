// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License. You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

'use strict';

const assert = require('node:assert/strict');
const braces = require('braces');

assert.equal(require('braces/package.json').version, '3.0.3');

const nested = depth => '{'.repeat(depth) + 'a' + '}'.repeat(depth);
const boundary = braces.parse(nested(100));
assert.doesNotThrow(() => braces.compile(boundary));
assert.doesNotThrow(() => braces.expand(braces.parse(nested(100))));
assert.doesNotThrow(() => braces.stringify(boundary));
assert.throws(() => braces.parse(nested(101)), /exceeds max depth \(100\)/);
assert.doesNotThrow(() => braces.parse('('.repeat(100) + 'a' + ')'.repeat(100)));
assert.throws(() => braces.parse('('.repeat(101) + 'a' + ')'.repeat(101)), /exceeds max depth \(100\)/);
assert.throws(() => braces.parse('{{a,b},c}', { maxDepth: 1.5 }), /exceeds max depth \(1.5\)/);

const deepAst = () => {
  let node = { type: 'text', value: 'a' };
  for (let depth = 0; depth < 101; depth++) {
    node = { type: 'brace', open: true, close: true, nodes: [node] };
  }
  return node;
};

for (const method of ['compile', 'expand', 'stringify']) {
  assert.throws(() => braces[method](deepAst()), /exceeds max depth \(100\)/, method);
}

assert.equal(braces.stringify(braces.parse('{{a}}'), { escapeInvalid: true }), '{{a}}');
console.log('braces depth patch verified');
