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

import { assertType, expect, expectTypeOf, it } from 'vitest';

import {
  DatasetType,
  ParagraphItem,
  ParagraphResultCode,
  ParagraphState,
  ParagraphStatus
} from './message-paragraph.interface';

it('matches the finite values in Job.Status and InterpreterResult', () => {
  expectTypeOf<ParagraphItem['status']>().toEqualTypeOf<
    'UNKNOWN' | 'READY' | 'PENDING' | 'RUNNING' | 'FINISHED' | 'ERROR' | 'ABORT'
  >();
  expectTypeOf<ParagraphStatus['status']>().toEqualTypeOf<ParagraphState>();
  expectTypeOf<NonNullable<ParagraphItem['results']>['code']>().toEqualTypeOf<ParagraphResultCode | undefined>();
  expectTypeOf<ParagraphResultCode>().toEqualTypeOf<'SUCCESS' | 'INCOMPLETE' | 'ERROR' | 'KEEP_PREVIOUS_RESULT'>();
  assertType<DatasetType>(DatasetType.SVG);
  assertType<DatasetType>(DatasetType.NULL);
});

it('declares all eight InterpreterResult.Type wire values', () => {
  expect(Object.values(DatasetType).sort()).toEqual([
    'ANGULAR',
    'HTML',
    'IMG',
    'NETWORK',
    'NULL',
    'SVG',
    'TABLE',
    'TEXT'
  ]);
});
