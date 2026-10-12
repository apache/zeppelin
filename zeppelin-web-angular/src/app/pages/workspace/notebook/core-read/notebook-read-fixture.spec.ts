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
import { Observable, Subject } from 'rxjs';
import { filter } from 'rxjs/operators';
import { describe, expect, it } from 'vitest';

import { createNotebookReadStore, NotebookReadSnapshot, NotebookReadTarget } from '@zeppelin/notebook-core';
import { MessageReceiveDataTypeMap, WebSocketMessage } from '@zeppelin/sdk';

import { Permissions } from '@zeppelin/interfaces';

import readAnonymous from '../../../../../../e2e/fixtures/notebook-read/read-anonymous.json?raw';
import readDenied from '../../../../../../e2e/fixtures/notebook-read/read-denied.json?raw';
import { NotebookReadTransport } from './notebook-read-transport';

type Envelope = WebSocketMessage<MessageReceiveDataTypeMap>;
type FixtureRecord =
  | { kind: 'context'; context: { state: 'active' | 'inactive'; noteId: string; revisionId: string | null } }
  | { kind: 'websocket'; websocket: { direction: 'send' | 'receive'; payloadText: string } }
  | {
      kind: 'rest';
      rest: { direction: 'request' | 'response'; request: { url: string }; status?: number; bodyJson?: unknown };
    }
  | { kind: 'connection' };

const loadFixture = (raw: string): { records: FixtureRecord[] } => JSON.parse(raw);

const permissionsNoteId = (url: string) => /\/api\/notebook\/([^/]+)\/permissions$/.exec(url)?.[1];

/**
 * Drives the production transport and Core with a capture, in recorded order. The transport's
 * requests must match the recorded ones, and only recorded server frames and REST responses reach it.
 * Returns the snapshot of every route the capture visited, taken when the route was left or the
 * capture ended.
 */
const replay = (records: FixtureRecord[]): NotebookReadSnapshot[] => {
  const received = new Subject<Envelope>();
  const sentReads = records.filter(
    record =>
      record.kind === 'websocket' &&
      record.websocket.direction === 'send' &&
      ['GET_NOTE', 'NOTE_REVISION'].includes(JSON.parse(record.websocket.payloadText).op)
  );
  let nextRead = 0;
  const send = (op: string, noteId: string, revisionId?: string) => {
    const recorded = JSON.parse(
      (sentReads[nextRead++] as Extract<FixtureRecord, { kind: 'websocket' }>).websocket.payloadText
    );
    expect({ op, noteId, revisionId }).toEqual({
      op: recorded.op,
      noteId: recorded.data.id ?? recorded.data.noteId,
      revisionId: recorded.data.revisionId
    });
    return recorded.msgId as string;
  };
  const pendingAcl = new Map<string, Subject<Permissions>>();
  const socket = {
    getNote: (noteId: string) => send('GET_NOTE', noteId),
    noteRevision: (noteId: string, revisionId: string) => send('NOTE_REVISION', noteId, revisionId),
    receiveEnvelope: <K extends keyof MessageReceiveDataTypeMap>(op: K) =>
      received.pipe(filter(message => message.op === op)) as Observable<WebSocketMessage<MessageReceiveDataTypeMap, K>>
  };
  const aclSource = {
    getPermissions: (noteId: string) => {
      const response = new Subject<Permissions>();
      pendingAcl.set(noteId, response);
      return response;
    }
  };
  const store = createNotebookReadStore();
  const transport = new NotebookReadTransport(socket, aclSource);
  const visited: NotebookReadSnapshot[] = [];

  for (const record of records) {
    if (record.kind === 'context') {
      const { state, noteId, revisionId } = record.context;
      if (state === 'active') {
        const target: NotebookReadTarget = revisionId
          ? { kind: 'revision', noteId, revisionId }
          : { kind: 'note', noteId };
        transport.load(store.activate(target));
      } else {
        visited.push(store.getSnapshot());
      }
    } else if (record.kind === 'websocket' && record.websocket.direction === 'receive') {
      received.next(JSON.parse(record.websocket.payloadText));
    } else if (record.kind === 'rest' && record.rest.direction === 'response') {
      const noteId = permissionsNoteId(record.rest.request.url);
      const response = noteId === undefined ? undefined : pendingAcl.get(noteId);
      if (response && record.rest.status === 200) {
        // The Shell's HTTP interceptor unwraps the JsonResponse envelope before services see it.
        response.next((record.rest.bodyJson as { body: Permissions }).body);
        response.complete();
      } else if (response) {
        response.error(new HttpErrorResponse({ status: record.rest.status }));
      }
    }
  }
  visited.push(store.getSnapshot());
  transport.cancel();
  expect(nextRead).toBe(sentReads.length);
  return visited;
};

describe('captured notebook read replies', () => {
  it('load a live note and a revision, and fail a missing note, through the transport and Core', () => {
    const { records } = loadFixture(readAnonymous);
    const note = records
      .filter((record): record is Extract<FixtureRecord, { kind: 'websocket' }> => record.kind === 'websocket')
      .map(record => JSON.parse(record.websocket.payloadText))
      .find(message => message.op === 'NOTE').data.note;

    const [live, revision, missing] = replay(records);

    expect(live).toMatchObject({ status: 'ready', revisionId: null, acl: { status: 'ready' } });
    expect(live.note?.paragraphIds).toEqual(note.paragraphs.map((p: { id: string }) => p.id));
    expect(live.note?.paragraphsById[note.paragraphs[1].id].chartConfigs['0']).toMatchObject({ mode: 'lineChart' });
    expect(revision).toMatchObject({ status: 'ready', acl: { status: 'ready' } });
    expect(revision.revisionId).toEqual(expect.any(String));
    expect(missing).toMatchObject({ status: 'failed', note: null });
  });

  it('report a read the note permissions deny as access-denied, with permissions denied too', () => {
    const [denied] = replay(loadFixture(readDenied).records);

    expect(denied).toMatchObject({ status: 'access-denied', note: null, acl: { status: 'access-denied' } });
  });
});
