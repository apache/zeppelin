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

import { DatasetType, ParagraphIResultsMsgItem } from '@zeppelin/sdk';
import { describe, expect, it } from 'vitest';

import capture from './paragraph-output-stream.capture.json';
import { ParagraphOutputState } from './paragraph-output-state';

interface CapturedEvent {
  op: string;
  data: {
    index?: number;
    type?: string;
    data?: string;
    paragraph?: {
      status: string;
      results?: {
        msg?: Array<{ type: string; data: string }>;
      };
    };
  };
}

const capturedType = (type: string): DatasetType => {
  expect(Object.values(DatasetType)).toContain(type);
  return type as DatasetType;
};

const capturedResults = (results: Array<{ type: string; data: string }>): ParagraphIResultsMsgItem[] =>
  results.map(result => ({ type: capturedType(result.type), data: result.data }));

const replay = (state: ParagraphOutputState, events: CapturedEvent[]): string[] => {
  const rendered: string[] = [];
  for (const event of events) {
    if (event.op === 'PARAGRAPH_UPDATE_OUTPUT') {
      const result = state.update(event.data.index!, capturedType(event.data.type!), event.data.data!);
      if (result) {
        rendered.push(result.data);
      }
    } else if (event.op === 'PARAGRAPH_APPEND_OUTPUT') {
      const result = state.append(event.data.index!, event.data.data!);
      if (result) {
        rendered.push(result.data);
      }
    } else if (event.op === 'PARAGRAPH') {
      state.finish(capturedResults(event.data.paragraph?.results?.msg ?? []));
    }
  }
  return rendered;
};

describe('ParagraphOutputState', () => {
  it('replays the captured callback and WebSocket order without dropping output', () => {
    const state = new ParagraphOutputState();
    state.reset();
    const events = capture.enabled.events as CapturedEvent[];

    expect(capture.schemaVersion).toBe(1);
    expect(events.slice(0, -1).map(({ op, data }) => ({ op, data }))).toEqual(capture.callbackOrder.events);
    expect(replay(state, events)).toEqual(['', 'first\n', 'first\nsecond\n', 'first\nsecond\nthird\n']);
    expect(state.snapshot()).toEqual([{ type: DatasetType.TEXT, data: 'first\nsecond\nthird\n' }]);
  });

  it('accumulates coalesced APPEND chunks at their result index', () => {
    const state = new ParagraphOutputState();
    const appends = capture.enabled.events.filter(event => event.op === 'PARAGRAPH_APPEND_OUTPUT');
    state.reset([{ type: DatasetType.TEXT, data: '' }]);

    state.append(0, appends[0].data.data + appends[1].data.data);
    const result = state.append(0, appends[2].data.data);

    expect(result).toEqual({ type: DatasetType.TEXT, data: 'first\nsecond\nthird\n' });
    expect(state.snapshot()).toEqual([result]);
  });

  it('holds APPEND chunks until a typed UPDATE can render them', () => {
    const state = new ParagraphOutputState();
    const update = capture.enabled.events.find(event => event.op === 'PARAGRAPH_UPDATE_OUTPUT')!;
    const appends = capture.enabled.events.filter(event => event.op === 'PARAGRAPH_APPEND_OUTPUT');
    state.reset();

    expect(state.append(0, appends[0].data.data)).toBeUndefined();
    expect(state.append(0, appends[1].data.data)).toBeUndefined();

    expect(state.update(0, capturedType(update.data.type), update.data.data)).toEqual({
      type: DatasetType.TEXT,
      data: 'first\nsecond\n'
    });
  });

  it('uses the terminal snapshot after UPDATE overtakes a queued APPEND', () => {
    const state = new ParagraphOutputState();
    state.reset([{ type: DatasetType.TEXT, data: 'stale\n' }]);

    state.update(0, DatasetType.TEXT, 'replacement\n');
    state.append(0, 'queued-before-update\n');
    state.finish([{ type: DatasetType.TEXT, data: 'replacement\n' }]);

    expect(state.snapshot()).toEqual([{ type: DatasetType.TEXT, data: 'replacement\n' }]);
  });

  it('ignores an APPEND observed after the terminal PARAGRAPH', () => {
    const state = new ParagraphOutputState();
    const events = capture.enabled.events as CapturedEvent[];
    const terminal = events.find(event => event.op === 'PARAGRAPH')!;
    const finalAppend = events.findLast(event => event.op === 'PARAGRAPH_APPEND_OUTPUT')!;
    state.reset([{ type: DatasetType.TEXT, data: 'first\nsecond\n' }]);

    replay(state, [terminal, finalAppend]);

    expect(state.snapshot()).toEqual([{ type: DatasetType.TEXT, data: 'first\nsecond\nthird\n' }]);
  });

  it('falls back to the terminal snapshot when streaming messages are disabled', () => {
    const state = new ParagraphOutputState();
    const events = capture.disabled.events as CapturedEvent[];
    state.reset();

    expect(capture.disabled.configuration['zeppelin.websocket.paragraph_status_progress.enable']).toBe(false);
    expect(events.map(event => event.op)).toEqual(['PARAGRAPH']);
    expect(() => replay(state, events)).not.toThrow();
    expect(state.snapshot()).toEqual([{ type: DatasetType.TEXT, data: 'first\nsecond\nthird\n' }]);
  });
});
