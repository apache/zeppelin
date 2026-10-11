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

import { assertType, describe, expect, it } from 'vitest';

import type { NotebookCorePort } from './host-remote-contract';
import type { NotebookNoteInput, NotebookParagraphInput, NotebookReadSnapshot } from './read-model';
import { createNotebookReadStore } from './read-store';

const paragraph = (id: string, overrides: Partial<NotebookParagraphInput> = {}): NotebookParagraphInput => ({
  id,
  title: `${id} title`,
  text: `%md ${id}`,
  status: 'FINISHED',
  config: { results: { '0': { graph: { mode: 'table', height: 300 } } } },
  results: { code: 'SUCCESS', msg: [{ type: 'TEXT', data: `${id} output` }] },
  ...overrides
});

const note = (
  id: string,
  paragraphs: NotebookParagraphInput[] = [paragraph('p1'), paragraph('p2')]
): NotebookNoteInput => ({
  id,
  name: `${id} name`,
  path: `/${id}`,
  paragraphs
});

const acl = { owners: ['alice'], readers: ['bob'], runners: [], writers: ['carol'] };

const recordNotifications = (store: ReturnType<typeof createNotebookReadStore>) => {
  const snapshots: NotebookReadSnapshot[] = [];
  store.subscribe(() => snapshots.push(store.getSnapshot()));
  return snapshots;
};

// assertType does not invoke this callback; tsc checks the rejected mutations.
assertType<(snapshot: NotebookReadSnapshot) => void>(snapshot => {
  // @ts-expect-error The read status is readonly.
  snapshot.status = 'ready';
  // @ts-expect-error The paragraph order is readonly.
  snapshot.note?.paragraphIds.push('p3');
  // @ts-expect-error Paragraph state is readonly.
  snapshot.note!.paragraphsById.p1.text = 'changed';
  // @ts-expect-error Permission lists are readonly.
  snapshot.acl.owners.push('mallory');
});

// The store's snapshot extends the NotebookPort snapshot, so the store can serve as a port.
assertType<NotebookCorePort>(createNotebookReadStore());

describe('notebook read store', () => {
  it('starts idle with no target and unknown permissions', () => {
    const snapshot = createNotebookReadStore().getSnapshot();

    expect(snapshot).toEqual({
      noteId: '',
      revisionId: null,
      target: null,
      status: 'idle',
      note: null,
      acl: { status: 'unknown', owners: [], readers: [], runners: [], writers: [] }
    });
  });

  it('loads a live note into ordered paragraphs with saved results and chart settings', () => {
    const store = createNotebookReadStore();
    const request = store.activate({ kind: 'note', noteId: 'note-a' });
    expect(store.getSnapshot()).toMatchObject({ noteId: 'note-a', revisionId: null, status: 'loading', note: null });

    expect(request.acceptNote(note('note-a', [paragraph('p2'), paragraph('p1', { title: undefined })]))).toBe(true);

    const snapshot = store.getSnapshot();
    expect(snapshot.status).toBe('ready');
    expect(snapshot.note).toMatchObject({
      id: 'note-a',
      name: 'note-a name',
      path: '/note-a',
      paragraphIds: ['p2', 'p1']
    });
    expect(snapshot.note?.paragraphsById.p1).toEqual({
      id: 'p1',
      title: null,
      text: '%md p1',
      status: 'FINISHED',
      resultCode: 'SUCCESS',
      resultMessages: [{ type: 'TEXT', data: 'p1 output' }],
      chartConfigs: { '0': { mode: 'table', height: 300 } }
    });
  });

  it.each(['not-found', 'access-denied', 'failed'] as const)('reports a %s load outcome without a note', reason => {
    const store = createNotebookReadStore();
    const request = store.activate({ kind: 'note', noteId: 'note-a' });

    expect(request.fail(reason)).toBe(true);

    expect(store.getSnapshot()).toMatchObject({ noteId: 'note-a', status: reason, note: null });
  });

  it('ends a request at its first failure', () => {
    const store = createNotebookReadStore();
    const request = store.activate({ kind: 'note', noteId: 'note-a' });
    request.fail('failed');

    expect(request.acceptNote(note('note-a'))).toBe(false);
    expect(request.fail('not-found')).toBe(false);
    expect(store.getSnapshot().status).toBe('failed');
  });

  it('does not report a failure for a note that has already loaded', () => {
    const store = createNotebookReadStore();
    const request = store.activate({ kind: 'note', noteId: 'note-a' });
    request.acceptNote(note('note-a'));

    expect(request.fail('failed')).toBe(false);
    expect(store.getSnapshot().status).toBe('ready');
  });

  describe('snapshot identity and immutability', () => {
    it('returns the same snapshot until the state changes, and ignores an identical full note', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'note', noteId: 'note-a' });
      request.acceptNote(note('note-a'));
      const notifications = recordNotifications(store);
      const snapshot = store.getSnapshot();

      expect(store.getSnapshot()).toBe(snapshot);
      expect(request.acceptNote(note('note-a'))).toBe(true);
      expect(store.getSnapshot()).toBe(snapshot);
      expect(notifications).toEqual([]);
    });

    it('keeps unchanged paragraphs and order when a full note replaces the loaded one', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'note', noteId: 'note-a' });
      request.acceptNote(note('note-a'));
      const before = store.getSnapshot().note!;

      request.acceptNote(note('note-a', [paragraph('p1'), paragraph('p2', { text: '%md changed' })]));
      const after = store.getSnapshot().note!;

      expect(after).not.toBe(before);
      expect(after.paragraphIds).toBe(before.paragraphIds);
      expect(after.paragraphsById.p1).toBe(before.paragraphsById.p1);
      expect(after.paragraphsById.p2).not.toBe(before.paragraphsById.p2);
      expect(after.paragraphsById.p2.text).toBe('%md changed');
    });

    it('keeps paragraph objects when only their order changes', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'note', noteId: 'note-a' });
      request.acceptNote(note('note-a'));
      const before = store.getSnapshot().note!;

      request.acceptNote(note('note-a', [paragraph('p2'), paragraph('p1')]));
      const after = store.getSnapshot().note!;

      expect(after.paragraphIds).toEqual(['p2', 'p1']);
      expect(after.paragraphsById.p1).toBe(before.paragraphsById.p1);
      expect(after.paragraphsById.p2).toBe(before.paragraphsById.p2);
    });

    it('rejects nested mutation of a published snapshot', () => {
      const store = createNotebookReadStore();
      store.activate({ kind: 'note', noteId: 'note-a' }).acceptNote(note('note-a'));
      const snapshot = store.getSnapshot() as unknown as {
        note: {
          paragraphIds: string[];
          paragraphsById: { [id: string]: { text: string; chartConfigs: { [i: string]: { mode: string } } } };
        };
      };

      expect(() => {
        snapshot.note.paragraphIds.push('p3');
      }).toThrow(TypeError);
      expect(() => {
        snapshot.note.paragraphsById.p1.text = 'changed';
      }).toThrow(TypeError);
      expect(() => {
        snapshot.note.paragraphsById.p1.chartConfigs['0'].mode = 'lineChart';
      }).toThrow(TypeError);
    });

    it('freezes the permission lists of every permissions state', () => {
      const store = createNotebookReadStore();
      const ownersOf = () => store.getSnapshot().acl.owners as string[];
      expect(() => ownersOf().push('mallory')).toThrow(TypeError);

      const aclRequest = store.activate({ kind: 'note', noteId: 'note-a' }).readAcl();
      expect(() => ownersOf().push('mallory')).toThrow(TypeError);

      aclRequest.fail('failed');
      expect(() => ownersOf().push('mallory')).toThrow(TypeError);

      store.activate({ kind: 'note', noteId: 'note-a' }).readAcl().accept(acl);
      expect(() => ownersOf().push('mallory')).toThrow(TypeError);
    });

    // An imported note keeps its paragraph IDs, so wire keys can be special property names.
    it('keeps a paragraph whose ID is __proto__ and updates it from a later full note', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'note', noteId: 'note-a' });
      request.acceptNote(note('note-a', [paragraph('__proto__'), paragraph('p1')]));
      const before = store.getSnapshot().note!;

      expect(before.paragraphIds).toEqual(['__proto__', 'p1']);
      expect(Object.prototype.hasOwnProperty.call(before.paragraphsById, '__proto__')).toBe(true);
      expect(Object.getPrototypeOf(before.paragraphsById)).toBe(Object.prototype);
      expect(before.paragraphsById['__proto__'].text).toBe('%md __proto__');

      request.acceptNote(note('note-a', [paragraph('__proto__', { text: '%md changed' }), paragraph('p1')]));
      const after = store.getSnapshot().note!;

      expect(after.paragraphsById['__proto__'].text).toBe('%md changed');
      expect(after.paragraphsById.p1).toBe(before.paragraphsById.p1);
    });

    it('keeps __proto__ keys inside persisted chart settings', () => {
      const store = createNotebookReadStore();
      // JSON.parse creates `__proto__` as an own key, as a wire payload would.
      const results = JSON.parse('{"__proto__": {"graph": {"mode": "table", "__proto__": {"height": 1}}}}');
      store
        .activate({ kind: 'note', noteId: 'note-a' })
        .acceptNote(note('note-a', [paragraph('p1', { config: { results } })]));

      const chartConfigs = store.getSnapshot().note!.paragraphsById.p1.chartConfigs;
      expect(Object.keys(chartConfigs)).toEqual(['__proto__']);
      const graph = chartConfigs['__proto__'] as { [key: string]: unknown };
      expect(Object.keys(graph)).toEqual(['mode', '__proto__']);
      expect(graph['__proto__']).toEqual({ height: 1 });
    });

    it('keeps no reference to the host input', () => {
      const store = createNotebookReadStore();
      const input = note('note-a');
      store.activate({ kind: 'note', noteId: 'note-a' }).acceptNote(input);

      (input.paragraphs[0].config!.results!['0'].graph as { mode: string }).mode = 'pieChart';
      (input.paragraphs as NotebookParagraphInput[]).push(paragraph('p3'));

      expect(store.getSnapshot().note?.paragraphIds).toEqual(['p1', 'p2']);
      expect(store.getSnapshot().note?.paragraphsById.p1.chartConfigs['0']).toEqual({ mode: 'table', height: 300 });
      expect(Object.isFrozen(input)).toBe(false);
    });
  });

  describe('subscriptions', () => {
    it('notifies each listener once per change, in subscription order', () => {
      const store = createNotebookReadStore();
      const calls: string[] = [];
      store.subscribe(() => calls.push('first'));
      store.subscribe(() => calls.push('second'));

      store.activate({ kind: 'note', noteId: 'note-a' }).acceptNote(note('note-a'));

      expect(calls).toEqual(['first', 'second', 'first', 'second']);
    });

    it('calls a listener subscribed while notifying from the next change', () => {
      const store = createNotebookReadStore();
      const calls: string[] = [];
      let subscribedLate = false;
      store.subscribe(() => {
        calls.push('first');
        if (!subscribedLate) {
          subscribedLate = true;
          store.subscribe(() => calls.push('late'));
        }
      });

      const request = store.activate({ kind: 'note', noteId: 'note-a' });
      expect(calls).toEqual(['first']);

      request.acceptNote(note('note-a'));
      expect(calls).toEqual(['first', 'first', 'late']);
    });

    it('still calls a listener unsubscribed while notifying, then stops', () => {
      const store = createNotebookReadStore();
      const calls: string[] = [];
      let unsubscribeSecond = () => {};
      store.subscribe(() => {
        calls.push('first');
        unsubscribeSecond();
      });
      unsubscribeSecond = store.subscribe(() => calls.push('second'));

      const request = store.activate({ kind: 'note', noteId: 'note-a' });
      expect(calls).toEqual(['first', 'second']);

      request.acceptNote(note('note-a'));
      expect(calls).toEqual(['first', 'second', 'first']);
    });

    it('stops notifying after unsubscribe, one subscription at a time', () => {
      const store = createNotebookReadStore();
      let calls = 0;
      const listener = () => {
        calls += 1;
      };
      const unsubscribeFirst = store.subscribe(listener);
      const unsubscribeSecond = store.subscribe(listener);

      unsubscribeFirst();
      store.activate({ kind: 'note', noteId: 'note-a' });
      expect(calls).toBe(1);

      unsubscribeSecond();
      store.deactivate();
      expect(calls).toBe(1);
    });

    it('publishes a final disposed snapshot, then drops listeners and rejects every handle', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'note', noteId: 'note-a' });
      const aclRequest = request.readAcl();
      const notifications = recordNotifications(store);

      store.dispose();

      expect(notifications.map(snapshot => snapshot.status)).toEqual(['disposed']);
      expect(request.isCurrent()).toBe(false);
      expect(request.acceptNote(note('note-a'))).toBe(false);
      expect(aclRequest.accept(acl)).toBe(false);
      store.subscribe(() => notifications.push(store.getSnapshot()));
      store.deactivate();
      store.dispose();
      expect(notifications).toHaveLength(1);
      expect(() => store.activate({ kind: 'note', noteId: 'note-a' })).toThrow(
        'Cannot activate a disposed notebook read store'
      );
    });
  });

  // These handles are a synthetic request context: the host creates one when it sends a request
  // and must route the response to it. The current wire cannot always do that. A NOTE carries no
  // msgId and the same op is also broadcast on note changes, so a host can attribute it only by
  // note id. These tests pin the Core's contract for responses the host has attributed; whether the
  // host can attribute them is verified with the real transport in ZEPPELIN-6737.
  describe('request handles (synthetic request context)', () => {
    it('rejects a NOTE for another note and a NOTE_REVISION for a live-note target', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'note', noteId: 'note-a' });

      expect(request.acceptNote(note('note-b'))).toBe(false);
      expect(request.acceptRevision({ noteId: 'note-a', revisionId: 'r1', note: note('note-a') })).toBe(false);
      expect(store.getSnapshot().status).toBe('loading');
    });

    it('accepts only the matching NOTE_REVISION for a revision target', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'revision', noteId: 'note-a', revisionId: 'r1' });

      expect(request.acceptNote(note('note-a'))).toBe(false);
      expect(request.acceptRevision({ noteId: 'note-a', revisionId: 'r2', note: note('note-a') })).toBe(false);
      expect(request.acceptRevision({ noteId: 'note-a', revisionId: 'r1', note: note('note-b') })).toBe(false);
      expect(store.getSnapshot().status).toBe('loading');

      expect(request.acceptRevision({ noteId: 'note-a', revisionId: 'r1', note: note('note-a') })).toBe(true);
      expect(store.getSnapshot()).toMatchObject({ noteId: 'note-a', revisionId: 'r1', status: 'ready' });
    });

    // Notebook#getNoteByRevision returns no note both for a missing revision and when the default
    // repository does not support revisions, so the cause is unknown.
    it('reports a NOTE_REVISION without a note as failed rather than not found', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'revision', noteId: 'note-a', revisionId: 'r1' });

      expect(request.acceptRevision({ noteId: 'note-a', revisionId: 'r1' })).toBe(true);

      expect(store.getSnapshot()).toMatchObject({ status: 'failed', note: null });
    });

    it('switches between the live note and its revision, rejecting the replaced request', () => {
      const store = createNotebookReadStore();
      const live = store.activate({ kind: 'note', noteId: 'note-a' });
      live.acceptNote(note('note-a'));

      const revision = store.activate({ kind: 'revision', noteId: 'note-a', revisionId: 'r1' });
      expect(store.getSnapshot()).toMatchObject({ revisionId: 'r1', status: 'loading', note: null });
      expect(live.acceptNote(note('note-a'))).toBe(false);
      revision.acceptRevision({ noteId: 'note-a', revisionId: 'r1', note: note('note-a', [paragraph('p1')]) });

      const liveAgain = store.activate({ kind: 'note', noteId: 'note-a' });
      expect(revision.acceptRevision({ noteId: 'note-a', revisionId: 'r1', note: note('note-a') })).toBe(false);
      expect(liveAgain.acceptNote(note('note-a'))).toBe(true);
      expect(store.getSnapshot()).toMatchObject({ revisionId: null, status: 'ready' });
    });

    it('rejects the first visit after A -> B -> A, though its NOTE matches the target again', () => {
      const store = createNotebookReadStore();
      const firstVisit = store.activate({ kind: 'note', noteId: 'note-a' });
      store.activate({ kind: 'note', noteId: 'note-b' });
      const secondVisit = store.activate({ kind: 'note', noteId: 'note-a' });

      expect(firstVisit.acceptNote(note('note-a', [paragraph('stale')]))).toBe(false);
      expect(store.getSnapshot()).toMatchObject({ noteId: 'note-a', status: 'loading' });

      expect(secondVisit.acceptNote(note('note-a'))).toBe(true);
      expect(store.getSnapshot().note?.paragraphIds).toEqual(['p1', 'p2']);
    });

    it('rejects the earlier request when the same revision is requested again', () => {
      const store = createNotebookReadStore();
      const target = { kind: 'revision', noteId: 'note-a', revisionId: 'r1' } as const;
      const first = store.activate(target);
      const second = store.activate(target);

      expect(first.acceptRevision({ noteId: 'note-a', revisionId: 'r1', note: note('note-a') })).toBe(false);
      expect(second.acceptRevision({ noteId: 'note-a', revisionId: 'r1', note: note('note-a') })).toBe(true);
    });

    it('returns to idle on deactivate and rejects the replaced request', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'note', noteId: 'note-a' });

      store.deactivate();

      expect(store.getSnapshot()).toMatchObject({ target: null, noteId: '', status: 'idle' });
      expect(request.acceptNote(note('note-a'))).toBe(false);
      expect(request.readAcl().accept(acl)).toBe(false);
    });
  });

  describe('note permissions', () => {
    it('reads permissions for the current activation', () => {
      const store = createNotebookReadStore();
      const aclRequest = store.activate({ kind: 'note', noteId: 'note-a' }).readAcl();
      expect(store.getSnapshot().acl.status).toBe('loading');

      expect(aclRequest.accept(acl)).toBe(true);

      expect(store.getSnapshot().acl).toEqual({ status: 'ready', ...acl });
    });

    it.each(['access-denied', 'failed'] as const)('reports a %s permissions read without lists', reason => {
      const store = createNotebookReadStore();
      const aclRequest = store.activate({ kind: 'note', noteId: 'note-a' }).readAcl();

      expect(aclRequest.fail(reason)).toBe(true);

      expect(store.getSnapshot().acl).toEqual({ status: reason, owners: [], readers: [], runners: [], writers: [] });
    });

    it('rejects a replaced permissions read and one from a replaced activation', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'note', noteId: 'note-a' });
      const firstRead = request.readAcl();
      const secondRead = request.readAcl();

      expect(firstRead.accept(acl)).toBe(false);
      expect(secondRead.accept(acl)).toBe(true);

      const nextActivation = store.activate({ kind: 'note', noteId: 'note-a' });
      expect(secondRead.accept({ ...acl, owners: ['mallory'] })).toBe(false);
      expect(nextActivation.isCurrent()).toBe(true);
      expect(store.getSnapshot().acl.status).toBe('unknown');
    });

    it('keeps the permissions object when a read returns the same lists', () => {
      const store = createNotebookReadStore();
      const request = store.activate({ kind: 'note', noteId: 'note-a' });
      request.readAcl().accept(acl);
      const first = store.getSnapshot().acl;

      request.readAcl().accept({ ...acl });

      expect(store.getSnapshot().acl).toBe(first);
    });
  });
});
