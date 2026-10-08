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

import type { NotebookAclInput, NotebookNoteInput } from './read-model';
import { selectAclCapabilities, selectChartConfig, selectOrderedParagraphs, selectParagraph } from './read-selectors';
import { createNotebookReadStore } from './read-store';

const note: NotebookNoteInput = {
  id: 'note-a',
  name: 'note-a name',
  paragraphs: [
    { id: 'p2', status: 'READY' },
    {
      id: 'p1',
      status: 'FINISHED',
      config: { results: { '0': { graph: { mode: 'lineChart' } }, '1': {} } }
    }
  ]
};

const loadedStore = () => {
  const store = createNotebookReadStore();
  const request = store.activate({ kind: 'note', noteId: 'note-a' });
  request.acceptNote(note);
  return { store, request };
};

const withAcl = (acl: NotebookAclInput) => {
  const { store, request } = loadedStore();
  request.readAcl().accept(acl);
  return store.getSnapshot();
};

describe('notebook read selectors', () => {
  it('lists paragraphs in note order and reuses the list while the note is unchanged', () => {
    const { store, request } = loadedStore();
    const paragraphs = selectOrderedParagraphs(store.getSnapshot());

    expect(paragraphs.map(paragraph => paragraph.id)).toEqual(['p2', 'p1']);
    request.readAcl();
    expect(selectOrderedParagraphs(store.getSnapshot())).toBe(paragraphs);
    request.acceptNote({ ...note, paragraphs: [...note.paragraphs].reverse() });
    expect(selectOrderedParagraphs(store.getSnapshot()).map(paragraph => paragraph.id)).toEqual(['p1', 'p2']);
  });

  it('lists no paragraphs before a note loads', () => {
    const store = createNotebookReadStore();
    store.activate({ kind: 'note', noteId: 'note-a' });

    expect(selectOrderedParagraphs(store.getSnapshot())).toEqual([]);
    expect(selectOrderedParagraphs(store.getSnapshot())).toBe(
      selectOrderedParagraphs(createNotebookReadStore().getSnapshot())
    );
  });

  it('finds a paragraph and its persisted chart settings by id, ignoring inherited keys', () => {
    const snapshot = loadedStore().store.getSnapshot();

    expect(selectParagraph(snapshot, 'p1')?.status).toBe('FINISHED');
    expect(selectParagraph(snapshot, 'missing')).toBeUndefined();
    expect(selectParagraph(snapshot, 'constructor')).toBeUndefined();
    expect(selectChartConfig(snapshot, 'p1', 0)).toEqual({ mode: 'lineChart' });
    expect(selectChartConfig(snapshot, 'p1', 1)).toBeUndefined();
    expect(selectChartConfig(snapshot, 'p2', 0)).toBeUndefined();
  });

  it.each(['unknown', 'loading', 'access-denied', 'failed'])('grants nothing while permissions are %s', status => {
    const { store, request } = loadedStore();
    if (status !== 'unknown') {
      const aclRequest = request.readAcl();
      if (status !== 'loading') {
        aclRequest.fail(status as 'access-denied' | 'failed');
      }
    }

    expect(store.getSnapshot().acl.status).toBe(status);
    expect(selectAclCapabilities(store.getSnapshot(), ['alice'])).toEqual({
      read: false,
      run: false,
      write: false,
      own: false
    });
  });

  it.each([
    ['an owner', ['alice'], { read: true, run: true, write: true, own: true }],
    ['a writer', ['carol'], { read: true, run: true, write: true, own: false }],
    ['a runner through a role', ['dave', 'analysts'], { read: true, run: true, write: false, own: false }],
    ['a reader', ['bob'], { read: true, run: false, write: false, own: false }],
    ['anyone else', ['mallory'], { read: false, run: false, write: false, own: false }]
  ])('grants %s the capabilities the server lists allow', (_name, principals, expected) => {
    const snapshot = withAcl({ owners: ['alice'], writers: ['carol'], runners: ['analysts'], readers: ['bob'] });

    expect(selectAclCapabilities(snapshot, principals)).toEqual(expected);
  });

  it('treats an empty list as open to everyone, as the server does', () => {
    const snapshot = withAcl({ owners: ['alice'], writers: ['carol'], runners: ['dave'], readers: [] });

    expect(selectAclCapabilities(snapshot, ['mallory'])).toEqual({ read: true, run: false, write: false, own: false });
  });

  it('returns the same capabilities object for the same result', () => {
    const snapshot = withAcl({ owners: ['alice'], writers: [], runners: [], readers: [] });

    expect(selectAclCapabilities(snapshot, ['alice'])).toBe(selectAclCapabilities(snapshot, ['alice']));
    expect(Object.isFrozen(selectAclCapabilities(snapshot, ['alice']))).toBe(true);
  });
});
