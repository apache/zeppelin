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

import { createNotebookReadStore, selectOrderedParagraphs, NotebookParagraphInput } from '@zeppelin/notebook-core';

import { toPrincipals, toReadAccess, toReadParagraphView } from './notebook-core-read-view';

const load = (paragraphs: NotebookParagraphInput[]) => {
  const store = createNotebookReadStore();
  store.activate({ kind: 'note', noteId: 'A' }).acceptNote({ id: 'A', name: 'A', paragraphs });
  return store;
};

const firstParagraph = (store: ReturnType<typeof load>) => selectOrderedParagraphs(store.getSnapshot())[0];

describe('toReadParagraphView', () => {
  it('renders the supported saved result types and marks the rest unsupported', () => {
    const types = ['TABLE', 'TEXT', 'HTML', 'IMG', 'SVG', 'NULL', 'ANGULAR', 'NETWORK', 'SOMETHING_NEW'];
    const store = load([
      { id: 'p1', status: 'FINISHED', results: { code: 'SUCCESS', msg: types.map(type => ({ type, data: type })) } }
    ]);

    const { results } = toReadParagraphView(firstParagraph(store));

    expect(
      results.map(result => (result.kind === 'rendered' ? result.result.type : `unsupported ${result.type}`))
    ).toEqual([
      'TABLE',
      'TEXT',
      'HTML',
      'IMG',
      'SVG',
      'NULL',
      'unsupported ANGULAR',
      'unsupported NETWORK',
      'unsupported SOMETHING_NEW'
    ]);
  });

  it('passes each result its own persisted chart settings as a writable copy', () => {
    const graph = { mode: 'lineChart', height: 200, setting: { lineChart: { forceY: true } } };
    const store = load([
      {
        id: 'p1',
        status: 'FINISHED',
        config: { results: { '1': { graph } } },
        results: {
          msg: [
            { type: 'TABLE', data: 'a\tb' },
            { type: 'TABLE', data: 'c\td' }
          ]
        }
      }
    ]);
    const paragraph = firstParagraph(store);

    const [first, second] = toReadParagraphView(paragraph).results;

    expect(first).toMatchObject({ kind: 'rendered', config: undefined });
    expect(second).toMatchObject({ kind: 'rendered', config: { graph } });
    const config = second.kind === 'rendered' ? second.config! : undefined;
    expect(() => {
      config!.graph.height = 400;
    }).not.toThrow();
    expect(paragraph.chartConfigs['1']).toEqual(graph);
  });

  it('reuses the view of an unchanged paragraph and rebuilds a changed one', () => {
    const store = createNotebookReadStore();
    const request = store.activate({ kind: 'note', noteId: 'A' });
    const p = (id: string, text: string): NotebookParagraphInput => ({ id, text, status: 'FINISHED' });
    request.acceptNote({ id: 'A', name: 'A', paragraphs: [p('p1', 'one'), p('p2', 'two')] });
    const [before1, before2] = selectOrderedParagraphs(store.getSnapshot()).map(toReadParagraphView);

    request.acceptNote({ id: 'A', name: 'A', paragraphs: [p('p1', 'one'), p('p2', 'changed')] });
    const [after1, after2] = selectOrderedParagraphs(store.getSnapshot()).map(toReadParagraphView);

    expect(after1).toBe(before1);
    expect(after2).not.toBe(before2);
    expect(after2.paragraph.text).toBe('changed');
  });
});

describe('toReadAccess', () => {
  it.each([
    [{ own: true, write: true, run: true, read: true }, 'owner'],
    [{ own: false, write: true, run: true, read: true }, 'writer'],
    [{ own: false, write: false, run: true, read: true }, 'runner'],
    [{ own: false, write: false, run: false, read: true }, 'reader'],
    [{ own: false, write: false, run: false, read: false }, 'none']
  ] as const)('labels %o as %s', (capabilities, access) => {
    expect(toReadAccess(capabilities)).toBe(access);
  });
});

describe('toPrincipals', () => {
  it('adds the roles of the ticket to the principal', () => {
    expect(toPrincipals({ principal: 'alice', roles: '["admin","analyst"]' })).toEqual(['alice', 'admin', 'analyst']);
  });

  it.each(['', 'not json', '{"role":"admin"}', '[1, null]'])('ignores roles it cannot read: %j', roles => {
    expect(toPrincipals({ principal: 'alice', roles })).toEqual(['alice']);
  });
});
