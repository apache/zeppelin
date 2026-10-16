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

import type { AssistantAnswerMessage, AssistantMessage } from './assistantContract';
import { answerText, answerToolCalls, joinPages } from './messageHistory';

describe('joinPages', () => {
  it('joins a reply split across pages under the later id and retains both action logs', () => {
    const first = { id: 'a1', content: 'Looking.', toolCalls: [{ id: 'c1', name: 'read' }] };
    const second = { id: 'a2', content: 'Done.', toolCalls: [{ id: 'c2', name: 'summarize' }] };
    const earlier: AssistantMessage[] = [
      { id: 'u1', role: 'user', content: 'Read this notebook' },
      { id: 'a1', role: 'assistant', segments: [first] }
    ];
    const later: AssistantMessage[] = [{ id: 'a2', role: 'assistant', segments: [second] }];
    expect(joinPages(earlier, later)).toEqual([
      earlier[0],
      {
        id: 'a2',
        role: 'assistant',
        segments: [first, second]
      }
    ]);
    expect(answerText(earlier[1] as AssistantAnswerMessage)).toBe('Looking.');
    expect(answerText(later[0] as AssistantAnswerMessage)).toBe('Done.');
    expect(answerText(joinPages(earlier, later)[1] as AssistantAnswerMessage)).toBe('Looking.\n\nDone.');
    expect(answerToolCalls(joinPages(earlier, later)[1] as AssistantAnswerMessage)).toEqual([
      { id: 'c1', name: 'read' },
      { id: 'c2', name: 'summarize' }
    ]);
  });

  it('retains a tool-only reply across an empty intermediate page and deduplicates repeated calls', () => {
    const call = { id: 'c1', name: 'read' };
    const first = { id: 'a1', content: '', toolCalls: [call] };
    const second = { id: 'a2', content: 'Done.', toolCalls: [call] };
    const earlier: AssistantMessage[] = [{ id: 'a1', role: 'assistant', segments: [first] }];
    const later: AssistantMessage[] = [{ id: 'a2', role: 'assistant', segments: [second] }];
    expect(joinPages(joinPages(earlier, []), later)).toEqual([
      { id: 'a2', role: 'assistant', segments: [first, second] }
    ]);
    expect(answerToolCalls(joinPages(joinPages(earlier, []), later)[0] as AssistantAnswerMessage)).toEqual([call]);
  });

  it('keeps interleaved text and tool rounds in server order across an overlapping page boundary', () => {
    const rounds = [
      { id: 'a1', content: 'First thought.', toolCalls: [{ id: 'c1', name: 'read' }] },
      { id: 'a2', content: 'Second thought.', toolCalls: [{ id: 'c2', name: 'summarize' }] },
      { id: 'a3', content: 'Final answer.' }
    ];
    const earlier: AssistantMessage[] = [{ id: 'a2', role: 'assistant', segments: rounds.slice(0, 2) }];
    const later: AssistantMessage[] = [{ id: 'a3', role: 'assistant', segments: rounds.slice(1) }];
    expect(joinPages(earlier, later)).toEqual([
      {
        id: 'a3',
        role: 'assistant',
        segments: rounds
      }
    ]);
    const answer = joinPages(earlier, later)[0] as AssistantAnswerMessage;
    expect(answerText(answer)).toBe('First thought.\n\nSecond thought.\n\nFinal answer.');
    expect(answerToolCalls(answer)).toEqual([rounds[0].toolCalls![0], rounds[1].toolCalls![0]]);
  });

  it('keeps separate turns apart and handles empty history', () => {
    const earlier: AssistantMessage[] = [
      { id: 'a1', role: 'assistant', segments: [{ id: 'a1', content: 'First answer' }] }
    ];
    const later: AssistantMessage[] = [
      { id: 'u2', role: 'user', content: 'Another question' },
      { id: 'a2', role: 'assistant', segments: [{ id: 'a2', content: 'Another answer' }] }
    ];
    expect(joinPages(earlier, later)).toEqual([...earlier, ...later]);
    expect(joinPages([], later)).toEqual(later);
    expect(joinPages(earlier, [])).toEqual(earlier);
    expect(joinPages([], [])).toEqual([]);
  });

  it('keeps a question apart from its answer on the next page', () => {
    const earlier: AssistantMessage[] = [{ id: 'u1', role: 'user', content: 'Question' }];
    const later: AssistantMessage[] = [{ id: 'a1', role: 'assistant', segments: [{ id: 'a1', content: 'Answer' }] }];
    expect(joinPages(earlier, later)).toEqual([...earlier, ...later]);
  });
});
