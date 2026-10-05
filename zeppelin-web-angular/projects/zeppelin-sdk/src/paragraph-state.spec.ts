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

import { expect, it } from 'vitest';

import { ParagraphStates } from './interfaces/message-paragraph.interface';
import { isTerminalParagraphState } from './paragraph-state';

it.each(Object.values(ParagraphStates))('classifies %s using Job.Status.isCompleted', status => {
  expect(isTerminalParagraphState(status)).toBe(['FINISHED', 'ERROR', 'ABORT'].includes(status));
});

it('does not treat a missing paragraph as a completed run', () => {
  expect(isTerminalParagraphState(undefined)).toBe(false);
});
