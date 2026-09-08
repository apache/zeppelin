/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const {
  parseDataTypeMapOperations,
  parseJavaOperations,
  parseTypeScriptOperations,
  validateContract
} = require('./check-websocket-contract');

test('parses simple Java enum constants while ignoring comments', () => {
  const operations = parseJavaOperations(`
    public class Message {
      public enum OP {
        FIRST, // a comment containing },
        /* another comment containing }, */ SECOND,
        THIRD,
      }
    }
  `);

  assert.deepEqual([...operations], ['FIRST', 'SECOND', 'THIRD']);
});

test('accepts an optional Java enum semicolon', () => {
  const operations = parseJavaOperations('public enum OP { FIRST, SECOND; }');

  assert.deepEqual([...operations], ['FIRST', 'SECOND']);
});

test('fails closed for unsupported or malformed Java enum declarations', () => {
  assert.throws(() => parseJavaOperations('public enum OP { FIRST,,SECOND }'), /contains an empty operation/);
  assert.throws(
    () => parseJavaOperations('public enum OP { FIRST("value") }'),
    /must contain only simple enum constants/
  );
  assert.throws(() => parseJavaOperations('public enum OP { FIRST, \/\* unclosed'), /unclosed block comment/);
  assert.throws(
    () => parseJavaOperations('public enum OP { FIRST }\npublic enum OP { SECOND }'),
    /Expected exactly one public enum OP/
  );
});

test('classifies frontend-only TypeScript operations from inline JSDoc', () => {
  const operations = parseTypeScriptOperations(`
    export enum OP {
      WIRE = 'WIRE',
      /** @frontendOnly Emitted locally without using the websocket. */
      LOCAL = 'LOCAL'
    }
  `);

  assert.deepEqual([...operations.wireOperations], ['WIRE']);
  assert.deepEqual([...operations.frontendOnlyOperations], ['LOCAL']);
});

test('requires valid TypeScript wire values and frontend-only explanations', () => {
  assert.throws(
    () => parseTypeScriptOperations("export enum OP { FIRST = 'SECOND' }"),
    /must use the wire value 'FIRST'/
  );
  assert.throws(
    () =>
      parseTypeScriptOperations(`
        export enum OP {
          /** @frontendOnly */
          LOCAL = 'LOCAL'
        }
      `),
    /must explain why it is @frontendOnly/
  );
});

test('compares Java and TypeScript operation sets without requiring the same order', () => {
  const javaOperations = new Set(['FIRST', 'SECOND']);
  const typeScriptOperations = parseTypeScriptOperations(`
    export enum OP {
      SECOND = 'SECOND',
      FIRST = 'FIRST',
      /** @frontendOnly Emitted locally without using the websocket. */
      LOCAL = 'LOCAL'
    }
  `);
  const dataTypeMaps = `
    interface MessageSendDataTypeMap { [OP.FIRST]: undefined; }
    interface MessageReceiveDataTypeMap { [OP.SECOND]: undefined; [OP.LOCAL]: undefined; }
  `;
  const sendOperations = parseDataTypeMapOperations(dataTypeMaps, 'MessageSendDataTypeMap');
  const receiveOperations = parseDataTypeMapOperations(dataTypeMaps, 'MessageReceiveDataTypeMap');

  assert.doesNotThrow(() => validateContract(javaOperations, typeScriptOperations, sendOperations, receiveOperations));
});

test('reports operation drift and rejects frontend-only send operations', () => {
  const javaOperations = new Set(['FIRST', 'MISSING']);
  const typeScriptOperations = parseTypeScriptOperations(`
    export enum OP {
      FIRST = 'FIRST',
      EXTRA = 'EXTRA',
      /** @frontendOnly Emitted locally without using the websocket. */
      LOCAL = 'LOCAL'
    }
  `);

  assert.throws(
    () => validateContract(javaOperations, typeScriptOperations, new Set(), new Set(['LOCAL'])),
    /missing from TypeScript=\[MISSING\], extra in TypeScript=\[EXTRA\]/
  );

  const matchingTypeScriptOperations = parseTypeScriptOperations(`
    export enum OP {
      FIRST = 'FIRST',
      MISSING = 'MISSING',
      /** @frontendOnly Emitted locally without using the websocket. */
      LOCAL = 'LOCAL'
    }
  `);
  assert.throws(
    () => validateContract(javaOperations, matchingTypeScriptOperations, new Set(['LOCAL']), new Set(['LOCAL'])),
    /Frontend-only operation LOCAL cannot be in MessageSendDataTypeMap/
  );
});
