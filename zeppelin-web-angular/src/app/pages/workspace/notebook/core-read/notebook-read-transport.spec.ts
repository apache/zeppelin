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

import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, VirtualTimeScheduler } from 'rxjs';
import { filter } from 'rxjs/operators';
import { describe, expect, it } from 'vitest';

import { createNotebookReadStore, NotebookReadTarget } from '@zeppelin/notebook-core';
import { MessageReceiveDataTypeMap, OP, WebSocketMessage } from '@zeppelin/sdk';

import { Permissions } from '@zeppelin/interfaces';

import { NotebookReadSocket, NotebookReadTransport } from './notebook-read-transport';

type Envelope = WebSocketMessage<MessageReceiveDataTypeMap>;

const TIMEOUT_MS = 1000;

const note = (id: string, text = `${id} text`) => ({
  id,
  name: `${id} name`,
  path: `/${id}`,
  paragraphs: [{ id: `${id}-p1`, text, status: 'FINISHED' as const }]
});

const acl = (owner: string): Permissions => ({ owners: [owner], readers: [], runners: [], writers: [] });

const live = (noteId: string): NotebookReadTarget => ({ kind: 'note', noteId });

class FakeSocket implements NotebookReadSocket {
  readonly received = new Subject<Envelope>();
  readonly sent: Array<{ op: OP; msgId: string; noteId: string; revisionId?: string }> = [];
  /** Delivered synchronously from inside the send call, before it returns the msgId. */
  replyDuringSend: ((msgId: string) => Envelope) | null = null;
  private seq = 0;

  getNote(noteId: string): string {
    return this.send(OP.GET_NOTE, noteId);
  }

  noteRevision(noteId: string, revisionId: string): string {
    return this.send(OP.NOTE_REVISION, noteId, revisionId);
  }

  receiveEnvelope<K extends keyof MessageReceiveDataTypeMap>(
    op: K
  ): Observable<WebSocketMessage<MessageReceiveDataTypeMap, K>> {
    return this.received.pipe(filter(message => message.op === op)) as Observable<
      WebSocketMessage<MessageReceiveDataTypeMap, K>
    >;
  }

  reply(op: OP, data: unknown, msgId?: string): void {
    this.received.next({ op, data, msgId } as Envelope);
  }

  lastMsgId(): string {
    return this.sent[this.sent.length - 1].msgId;
  }

  private send(op: OP, noteId: string, revisionId?: string): string {
    const msgId = `client-${++this.seq}`;
    this.sent.push({ op, msgId, noteId, revisionId });
    if (this.replyDuringSend) {
      this.received.next(this.replyDuringSend(msgId));
    }
    return msgId;
  }
}

class FakeAclSource {
  readonly requests: Array<{ noteId: string; response: Subject<Permissions> }> = [];

  getPermissions(noteId: string): Observable<Permissions> {
    const response = new Subject<Permissions>();
    this.requests.push({ noteId, response });
    return response;
  }
}

const setup = () => {
  const socket = new FakeSocket();
  const aclSource = new FakeAclSource();
  const scheduler = new VirtualTimeScheduler();
  const store = createNotebookReadStore();
  const transport = new NotebookReadTransport(socket, aclSource, { timeoutMs: TIMEOUT_MS, scheduler });
  const load = (target: NotebookReadTarget) => {
    const request = store.activate(target);
    transport.load(request);
    return request;
  };
  let now = 0;
  const advance = (ms: number) => {
    now += ms;
    scheduler.maxFrames = now;
    scheduler.flush();
  };
  return { socket, aclSource, store, transport, load, advance };
};

describe('NotebookReadTransport', () => {
  it('sends GET_NOTE for a live note and NOTE_REVISION for a revision', () => {
    const { socket, load } = setup();

    load(live('A'));
    load({ kind: 'revision', noteId: 'A', revisionId: 'r1' });

    expect(socket.sent.map(({ op, noteId, revisionId }) => [op, noteId, revisionId])).toEqual([
      [OP.GET_NOTE, 'A', undefined],
      [OP.NOTE_REVISION, 'A', 'r1']
    ]);
  });

  it('accepts a reply delivered before the send returns, because it listens first', () => {
    const { socket, store, load } = setup();
    socket.replyDuringSend = msgId => ({ op: OP.NOTE, data: { note: note('A') }, msgId }) as Envelope;

    load(live('A'));

    expect(store.getSnapshot().status).toBe('ready');
    expect(store.getSnapshot().note?.id).toBe('A');
  });

  it('ignores NOTE broadcasts and replies to other requests', () => {
    const { socket, store, load } = setup();
    load(live('A'));

    socket.reply(OP.NOTE, { note: note('A') });
    socket.reply(OP.NOTE, { note: note('A') }, 'another-client-1');

    expect(store.getSnapshot().status).toBe('loading');

    socket.reply(OP.NOTE, { note: note('A') }, socket.lastMsgId());

    expect(store.getSnapshot().status).toBe('ready');
  });

  it('stops listening after its reply, so a duplicate or a later broadcast cannot overwrite it', () => {
    const { socket, store, load } = setup();
    load(live('A'));
    const msgId = socket.lastMsgId();
    socket.reply(OP.NOTE, { note: note('A') }, msgId);
    const loaded = store.getSnapshot();

    socket.reply(OP.NOTE, { note: note('A', 'changed') }, msgId);
    socket.reply(OP.NOTE, { note: note('A', 'broadcast') });

    expect(store.getSnapshot()).toBe(loaded);
    expect(socket.received.observed).toBe(false);
  });

  it('keeps a revision snapshot when the live note is broadcast', () => {
    const { socket, store, load } = setup();
    load({ kind: 'revision', noteId: 'A', revisionId: 'r1' });
    socket.reply(OP.NOTE_REVISION, { noteId: 'A', revisionId: 'r1', note: note('A', 'old') }, socket.lastMsgId());
    const loaded = store.getSnapshot();

    socket.reply(OP.NOTE, { note: note('A', 'live') });

    expect(store.getSnapshot()).toBe(loaded);
    expect(loaded.status).toBe('ready');
    expect(loaded.revisionId).toBe('r1');
  });

  it.each([
    [OP.AUTH_INFO, 'access-denied'],
    [OP.ERROR_INFO, 'failed']
  ] as const)('ends the read on its own %s as %s', (op, status) => {
    const { socket, store, load } = setup();
    load(live('A'));

    socket.reply(op, { info: 'denied' });
    expect(store.getSnapshot().status).toBe('loading');

    socket.reply(op, { info: 'denied' }, socket.lastMsgId());
    expect(store.getSnapshot().status).toBe(status);
  });

  it('treats a NOTE or NOTE_REVISION without a note as failed', () => {
    const { socket, store, load } = setup();
    load(live('A'));
    socket.reply(OP.NOTE, {}, socket.lastMsgId());
    expect(store.getSnapshot().status).toBe('failed');

    load({ kind: 'revision', noteId: 'A', revisionId: 'r1' });
    socket.reply(OP.NOTE_REVISION, { noteId: 'A', revisionId: 'r1' }, socket.lastMsgId());
    expect(store.getSnapshot().status).toBe('failed');
  });

  it('fails a read with no reply at the timeout and ignores the reply after it', () => {
    const { socket, store, load, advance } = setup();
    load(live('A'));
    const msgId = socket.lastMsgId();

    advance(TIMEOUT_MS - 1);
    expect(store.getSnapshot().status).toBe('loading');
    advance(1);
    expect(store.getSnapshot().status).toBe('failed');

    socket.reply(OP.NOTE, { note: note('A') }, msgId);
    expect(store.getSnapshot().status).toBe('failed');
  });

  it('does not time out a read that has completed', () => {
    const { socket, store, load, advance } = setup();
    load(live('A'));
    socket.reply(OP.NOTE, { note: note('A') }, socket.lastMsgId());

    advance(TIMEOUT_MS * 2);

    expect(store.getSnapshot().status).toBe('ready');
  });

  it('ignores the late reply of an earlier visit to the same note (A -> B -> A)', () => {
    const { socket, store, load } = setup();
    load(live('A'));
    const firstA = socket.lastMsgId();
    load(live('B'));
    const toB = socket.lastMsgId();
    load(live('A'));

    socket.reply(OP.NOTE, { note: note('A', 'first visit') }, firstA);
    socket.reply(OP.NOTE, { note: note('B') }, toB);
    expect(store.getSnapshot().status).toBe('loading');

    socket.reply(OP.NOTE, { note: note('A', 'second visit') }, socket.lastMsgId());
    expect(store.getSnapshot().note?.paragraphsById['A-p1'].text).toBe('second visit');
  });

  it('ignores replies once cancelled and stops listening', () => {
    const { socket, store, transport, load, advance } = setup();
    load(live('A'));
    const msgId = socket.lastMsgId();

    transport.cancel();
    socket.reply(OP.NOTE, { note: note('A') }, msgId);
    advance(TIMEOUT_MS);

    expect(store.getSnapshot().status).toBe('loading');
    expect(socket.received.observed).toBe(false);
    expect(socket.sent).toHaveLength(1);
  });

  describe('permissions', () => {
    it('reads the permissions of the target note', () => {
      const { aclSource, store, load } = setup();
      load(live('A'));

      aclSource.requests[0].response.next(acl('alice'));

      expect(aclSource.requests.map(({ noteId }) => noteId)).toEqual(['A']);
      expect(store.getSnapshot().acl).toMatchObject({ status: 'ready', owners: ['alice'] });
    });

    it.each([
      [403, 'access-denied'],
      [404, 'failed'],
      [500, 'failed']
    ] as const)('reports a %s response as %s', (status, aclStatus) => {
      const { aclSource, store, load } = setup();
      load(live('A'));

      aclSource.requests[0].response.error(new HttpErrorResponse({ status }));

      expect(store.getSnapshot().acl.status).toBe(aclStatus);
    });

    it('ignores the success and the failure of a permissions read for an earlier activation', () => {
      const { aclSource, store, load } = setup();
      load(live('A'));
      const [first] = aclSource.requests;
      load(live('B'));
      const [, second] = aclSource.requests;

      first.response.next(acl('alice'));
      expect(store.getSnapshot().acl.status).toBe('loading');
      first.response.error(new HttpErrorResponse({ status: 403 }));
      expect(store.getSnapshot().acl.status).toBe('loading');

      second.response.next(acl('bob'));
      expect(store.getSnapshot().acl).toMatchObject({ status: 'ready', owners: ['bob'] });
    });

    it('fails a permissions read with no response at the timeout', () => {
      const { aclSource, store, load, advance } = setup();
      load(live('A'));

      advance(TIMEOUT_MS);
      expect(store.getSnapshot().acl.status).toBe('failed');

      aclSource.requests[0].response.next(acl('alice'));
      expect(store.getSnapshot().acl.status).toBe('failed');
    });

    it('cancels the permissions request when the reads are cancelled', () => {
      const { aclSource, transport, load } = setup();
      load(live('A'));

      transport.cancel();

      expect(aclSource.requests[0].response.observed).toBe(false);
    });
  });
});
