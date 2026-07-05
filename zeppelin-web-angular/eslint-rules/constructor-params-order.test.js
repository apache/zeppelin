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

// Regression suite for the constructor-params-order fixer. It rewrites source
// files unattended via lint-staged on every commit, so the tricky reordering
// cases (comments, multi-line) are locked down here. Run with
// `npm run test:eslint-rules` (Node's built-in runner, no extra deps).

'use strict';

const test = require('node:test');
const { RuleTester } = require('eslint');
const tsParser = require('@typescript-eslint/parser');
const rule = require('./constructor-params-order');

const ruleTester = new RuleTester({
  languageOptions: { parser: tsParser, ecmaVersion: 2022, sourceType: 'module' }
});

test('constructor-params-order', () => {
  ruleTester.run('constructor-params-order', rule, {
    valid: [
      'class A { constructor(public a: X, protected b: Y, private c: Z) {} }',
      'class A { constructor(private a: X) {} }',
      'class A { constructor() {} }',
      // optional (@Optional() / question token) ranks last, so these are ordered
      'class A { constructor(public a: X, private c: Z, @Optional() private d: W) {} }',
      'class A { constructor(private a: X, @Optional() @Inject(T) public b: Y) {} }',
      'class A { constructor(private a: X, b?: Y) {} }',
      // a `readonly`-only parameter property is implicitly public, so it ranks first
      'class A { constructor(readonly a: X, private b: Y) {} }'
    ],
    invalid: [
      {
        code: 'class A { constructor(private c: Z, public a: X) {} }',
        output: 'class A { constructor(public a: X, private c: Z) {} }',
        errors: [{ messageId: 'order' }]
      },
      {
        code: 'class A { constructor(private c: Z, protected b: Y, public a: X) {} }',
        output: 'class A { constructor(public a: X, protected b: Y, private c: Z) {} }',
        errors: [{ messageId: 'order' }]
      },
      {
        code: 'class A { constructor(@Optional() private a: X, public b: Y) {} }',
        output: 'class A { constructor(public b: Y, @Optional() private a: X) {} }',
        errors: [{ messageId: 'order' }]
      },
      // a trailing comment before the comma must travel with its parameter
      {
        code: 'class A { constructor(private b: B /* injected lazily */, public a: A) {} }',
        output: 'class A { constructor(public a: A, private b: B /* injected lazily */) {} }',
        errors: [{ messageId: 'order' }]
      },
      // multi-line: original commas / indentation are preserved
      {
        code: 'class A {\n  constructor(\n    private c: Z,\n    public a: X\n  ) {}\n}',
        output: 'class A {\n  constructor(\n    public a: X,\n    private c: Z\n  ) {}\n}',
        errors: [{ messageId: 'order' }]
      },
      // a leading block comment stays attached to its parameter
      {
        code: 'class A {\n  constructor(\n    private a: A,\n    /* keep */ public b: B\n  ) {}\n}',
        output: 'class A {\n  constructor(\n    /* keep */ public b: B,\n    private a: A\n  ) {}\n}',
        errors: [{ messageId: 'order' }]
      },
      // a `readonly`-only parameter property is public and must precede private
      {
        code: 'class A { constructor(private a: A, readonly b: B) {} }',
        output: 'class A { constructor(readonly b: B, private a: A) {} }',
        errors: [{ messageId: 'order' }]
      }
    ]
  });
});
