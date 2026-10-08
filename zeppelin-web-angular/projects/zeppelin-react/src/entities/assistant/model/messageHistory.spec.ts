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

import { describe, expect, it } from 'vitest';

import type { AssistantMessage } from './assistantContract';
import { joinPages } from './messageHistory';

describe('joinPages', () => {
  it('joins a reply split across pages under the later id and retains both action logs', () => {
    const earlier: AssistantMessage[] = [
      { id: 'u1', role: 'user', content: 'Read this notebook' },
      { id: 'a1', role: 'assistant', content: 'Looking.', toolCalls: [{ id: 'c1', name: 'read' }] }
    ];
    const later: AssistantMessage[] = [
      { id: 'a2', role: 'assistant', content: 'Done.', toolCalls: [{ id: 'c2', name: 'summarize' }] }
    ];
    expect(joinPages(earlier, later)).toEqual([
      earlier[0],
      {
        id: 'a2',
        role: 'assistant',
        content: 'Looking.\n\nDone.',
        toolCalls: [
          { id: 'c1', name: 'read' },
          { id: 'c2', name: 'summarize' }
        ]
      }
    ]);
    expect(earlier[1].content).toBe('Looking.');
    expect(later[0].content).toBe('Done.');
  });

  it('retains a tool-only reply across an empty intermediate page and deduplicates repeated calls', () => {
    const call = { id: 'c1', name: 'read' };
    const earlier: AssistantMessage[] = [{ id: 'a1', role: 'assistant', content: '', toolCalls: [call] }];
    const later: AssistantMessage[] = [{ id: 'a2', role: 'assistant', content: 'Done.', toolCalls: [call] }];
    expect(joinPages(joinPages(earlier, []), later)).toEqual([
      { id: 'a2', role: 'assistant', content: 'Done.', toolCalls: [call] }
    ]);
  });

  it('keeps separate turns apart and handles empty history', () => {
    const earlier: AssistantMessage[] = [{ id: 'a1', role: 'assistant', content: 'First answer' }];
    const later: AssistantMessage[] = [
      { id: 'u2', role: 'user', content: 'Another question' },
      { id: 'a2', role: 'assistant', content: 'Another answer' }
    ];
    expect(joinPages(earlier, later)).toEqual([...earlier, ...later]);
    expect(joinPages([], later)).toEqual(later);
    expect(joinPages(earlier, [])).toEqual(earlier);
    expect(joinPages([], [])).toEqual([]);
  });
});
