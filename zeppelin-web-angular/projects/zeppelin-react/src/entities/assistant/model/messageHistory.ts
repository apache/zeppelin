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

import type { AssistantAnswerMessage, AssistantMessage, AssistantToolCallRef } from './assistantContract';

export const answerText = (answer: AssistantAnswerMessage): string =>
  answer.segments
    .map(segment => segment.content)
    .filter(Boolean)
    .join('\n\n');

export const answerToolCalls = (answer: AssistantAnswerMessage): AssistantToolCallRef[] =>
  Array.from(
    new Map(answer.segments.flatMap(segment => segment.toolCalls ?? []).map(call => [call.id, call])).values()
  );

export const mergeAnswers = (
  earlier: AssistantAnswerMessage,
  later: AssistantAnswerMessage
): AssistantAnswerMessage => {
  const segments = Array.from(
    new Map([...earlier.segments, ...later.segments].map(segment => [segment.id, segment])).values()
  );
  return {
    id: later.id,
    role: 'assistant',
    segments
  };
};

/**
 * Puts an earlier page in front of the loaded history. A turn split across the page boundary
 * becomes one bubble again, under the later id.
 */
export const joinPages = (earlier: AssistantMessage[], later: AssistantMessage[]): AssistantMessage[] => {
  const last = earlier[earlier.length - 1];
  const first = later[0];
  if (last?.role === 'assistant' && first?.role === 'assistant') {
    return [...earlier.slice(0, -1), mergeAnswers(last, first), ...later.slice(1)];
  }
  return [...earlier, ...later];
};
