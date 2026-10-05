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

import { expectTypeOf, it } from 'vitest';

import { CompletionItem } from './message-paragraph.interface';

it('accepts the Spark and Flink completion payload, which has no meta key', () => {
  expectTypeOf({ name: 'println', value: 'println' }).toExtend<CompletionItem>();
});

it('keeps name and value required', () => {
  expectTypeOf<CompletionItem>().toHaveProperty('name').toEqualTypeOf<string>();
  expectTypeOf<CompletionItem>().toHaveProperty('value').toEqualTypeOf<string>();
});
