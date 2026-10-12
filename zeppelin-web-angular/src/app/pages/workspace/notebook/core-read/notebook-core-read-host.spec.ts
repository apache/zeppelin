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

import { NEVER, Observable, Subject, VirtualTimeScheduler } from 'rxjs';
import { filter } from 'rxjs/operators';
import { describe, expect, it } from 'vitest';

import { MessageReceiveDataTypeMap, OP, WebSocketMessage } from '@zeppelin/sdk';

import { NotebookCoreReadHost, NotebookCoreReadState } from './notebook-core-read-host';

type Envelope = WebSocketMessage<MessageReceiveDataTypeMap>;

const note = (id: string) => ({ id, name: id, paragraphs: [] });

const setup = ({ connected = true } = {}) => {
  const received = new Subject<Envelope>();
  const connectedStatus$ = new Subject<boolean>();
  const sent: string[] = [];
  const connection = { connectedStatus: connected, connectedStatus$ };
  const socket = {
    getNote: (noteId: string) => {
      sent.push(`GET_NOTE ${noteId}`);
      return `msg-${sent.length}`;
    },
    noteRevision: (noteId: string, revisionId: string) => {
      sent.push(`NOTE_REVISION ${noteId}@${revisionId}`);
      return `msg-${sent.length}`;
    },
    receiveEnvelope: <K extends keyof MessageReceiveDataTypeMap>(op: K) =>
      received.pipe(filter(message => message.op === op)) as Observable<WebSocketMessage<MessageReceiveDataTypeMap, K>>
  };
  const aclSource = { getPermissions: () => NEVER };
  const host = new NotebookCoreReadHost(connection, socket, aclSource, { scheduler: new VirtualTimeScheduler() });
  const replyNote = (noteId: string) =>
    received.next({ op: OP.NOTE, data: { note: note(noteId) }, msgId: `msg-${sent.length}` } as Envelope);
  const states: NotebookCoreReadState[] = [];
  let completions = 0;
  host.state$.subscribe({ next: state => states.push(state), complete: () => completions++ });
  return { host, received, connectedStatus$, sent, replyNote, states, completions: () => completions };
};

describe('NotebookCoreReadHost', () => {
  it('keeps one port for its whole lifetime and publishes through it', () => {
    const { host, replyNote } = setup();
    const { port } = host;
    const notified: string[] = [];
    port.subscribe(() => notified.push(port.getSnapshot().noteId));

    host.open({ kind: 'note', noteId: 'A' });
    replyNote('A');
    host.open({ kind: 'note', noteId: 'B' });

    expect(host.port).toBe(port);
    expect(Object.isFrozen(port)).toBe(true);
    expect([...new Set(notified)]).toEqual(['A', 'B']);
  });

  it('publishes one state object per change and replays the latest to a new subscriber', () => {
    const { host, replyNote, states } = setup();
    host.open({ kind: 'note', noteId: 'A' });
    replyNote('A');

    let latest: NotebookCoreReadState | undefined;
    host.state$.subscribe(state => (latest = state)).unsubscribe();

    // The ACL read is a separate change, so `loading` is published more than once.
    expect([...new Set(states.map(({ snapshot }) => snapshot.status))]).toEqual(['idle', 'loading', 'ready']);
    expect(new Set(states.map(({ snapshot }) => snapshot)).size).toBe(states.length);
    expect(latest).toBe(states[states.length - 1]);
    expect(states[states.length - 1].snapshot).toBe(host.port.getSnapshot());
  });

  it('waits for the Shell to connect before it sends a read', () => {
    const { host, connectedStatus$, sent } = setup({ connected: false });

    host.open({ kind: 'note', noteId: 'A' });
    expect(sent).toEqual([]);

    connectedStatus$.next(true);
    expect(sent).toEqual(['GET_NOTE A']);
  });

  it('does not send anything for connection changes before a target is opened', () => {
    const { connectedStatus$, sent } = setup({ connected: false });

    connectedStatus$.next(true);
    connectedStatus$.next(false);

    expect(sent).toEqual([]);
  });

  it('invalidates a loaded note when the connection drops and reads it again on reconnect', () => {
    const { host, connectedStatus$, sent, replyNote, states } = setup();
    host.open({ kind: 'note', noteId: 'A' });
    replyNote('A');

    connectedStatus$.next(false);
    const lost = states[states.length - 1];
    expect(lost).toMatchObject({ connected: false, snapshot: { status: 'failed', note: null } });

    connectedStatus$.next(true);
    expect(sent).toEqual(['GET_NOTE A', 'GET_NOTE A']);
    expect(states[states.length - 1]).toMatchObject({ connected: true, snapshot: { status: 'loading' } });

    replyNote('A');
    expect(host.port.getSnapshot().status).toBe('ready');
    expect(new Set(states.map(({ snapshot }) => snapshot)).size).toBe(states.length);
  });

  it('ignores a reply that arrives after the connection dropped', () => {
    const { host, received, connectedStatus$, sent } = setup();
    host.open({ kind: 'note', noteId: 'A' });

    connectedStatus$.next(false);
    received.next({ op: OP.NOTE, data: { note: note('A') }, msgId: `msg-${sent.length}` } as Envelope);

    expect(host.port.getSnapshot().status).toBe('failed');
  });

  it('reads the current target again on retry', () => {
    const { host, sent } = setup();
    host.open({ kind: 'revision', noteId: 'A', revisionId: 'r1' });

    host.retry();

    expect(sent).toEqual(['NOTE_REVISION A@r1', 'NOTE_REVISION A@r1']);
  });

  it('tears down exactly once and ignores everything afterwards', () => {
    const { host, received, connectedStatus$, sent, replyNote, completions } = setup();
    host.open({ kind: 'note', noteId: 'A' });

    host.dispose();
    host.dispose();
    replyNote('A');
    host.open({ kind: 'note', noteId: 'B' });
    host.retry();
    connectedStatus$.next(false);
    connectedStatus$.next(true);

    expect(host.port.getSnapshot().status).toBe('disposed');
    expect(completions()).toBe(1);
    expect(sent).toEqual(['GET_NOTE A']);
    expect(received.observed).toBe(false);
    expect(connectedStatus$.observed).toBe(false);
  });
});
