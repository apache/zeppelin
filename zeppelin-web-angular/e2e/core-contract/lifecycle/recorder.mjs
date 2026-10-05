/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import {
  Subject,
  catchError,
  defer,
  filter,
  fromEvent,
  lastValueFrom,
  map,
  merge,
  mergeMap,
  of,
  scan,
  startWith,
  takeUntil,
  takeWhile
} from 'rxjs';
import {
  isNotebookRestUrl,
  captureRestRequest,
  parseRestBody,
  sanitizeFixture,
  stableJson,
  validateFixtureMetadata
} from '../transport/fixture.mjs';
import { validateLifecycleFixture, lifecycleFixtureVersion } from './validation.mjs';

async function settleRestResponse(request, outstandingRequests) {
  const response = await request.response();
  const headers = Object.fromEntries(
    Object.entries(response.headers()).filter(([name]) => ['accept', 'content-type'].includes(name.toLowerCase()))
  );
  const rest = {
    direction: 'response',
    request: captureRestRequest(request),
    status: response.status(),
    headers,
    ...parseRestBody(await response.text(), headers)
  };
  outstandingRequests.delete(request);
  return rest;
}

function observeRest(page, session) {
  const { id: sessionId, outstandingRequests } = session;
  const requests = new Map();
  let requestNumber = 0;
  const sent = fromEvent(page, 'request').pipe(
    filter(request => isNotebookRestUrl(request.url())),
    map(request => {
      const requestId = `${sessionId}:request:${++requestNumber}`;
      requests.set(request, requestId);
      outstandingRequests.add(request);
      return {
        entry: {
          kind: 'rest',
          sessionId,
          requestId,
          rest: { direction: 'request', request: captureRestRequest(request) }
        }
      };
    })
  );
  const finished = fromEvent(page, 'requestfinished').pipe(
    filter(request => requests.has(request)),
    map(request => {
      const requestId = requests.get(request);
      requests.delete(request);
      return {
        entry: { kind: 'rest', sessionId, requestId },
        readBody: () => settleRestResponse(request, outstandingRequests)
      };
    })
  );
  const failed = fromEvent(page, 'requestfailed').pipe(
    filter(request => requests.has(request)),
    map(() => {
      throw new Error('Notebook REST request failed during capture');
    })
  );
  return merge(sent, finished, failed);
}

function observeSocket(socket, session, connectionId) {
  const sessionId = session.id;
  session.activeConnection = connectionId;
  const frames = direction =>
    map(frame => {
      if (typeof frame.payload !== 'string') throw new Error('Binary lifecycle capture has no redaction policy');
      return {
        entry: { kind: 'websocket', sessionId, connectionId, websocket: { direction, payloadText: frame.payload } }
      };
    });
  const closed = fromEvent(socket, 'close').pipe(
    map(() => {
      if (session.activeConnection === connectionId) session.activeConnection = undefined;
      return { entry: { kind: 'connection', event: 'close', sessionId, connectionId } };
    })
  );
  const failed = fromEvent(socket, 'socketerror').pipe(
    map(error => {
      throw new Error(`Notebook socket error: ${error}`);
    })
  );
  return merge(
    fromEvent(socket, 'framesent').pipe(frames('send')),
    fromEvent(socket, 'framereceived').pipe(frames('receive')),
    closed,
    failed
  ).pipe(
    startWith({ entry: { kind: 'connection', event: 'open', sessionId, connectionId } }),
    takeWhile(observation => observation.entry.event !== 'close', true)
  );
}

function observePage(page, session) {
  let connectionNumber = 0;
  const sockets = fromEvent(page, 'websocket').pipe(
    filter(socket => new URL(socket.url()).pathname === '/ws'),
    mergeMap(socket => observeSocket(socket, session, `${session.id}:socket:${++connectionNumber}`))
  );
  return merge(observeRest(page, session), sockets).pipe(catchError(error => of({ error })));
}

function settleObservation({ entry, readBody, error }, index) {
  if (error) return of({ error });
  const record = { ...entry, sequence: index + 1 };
  if (!readBody) return of({ record });
  return defer(readBody).pipe(
    map(rest => ({ record: { ...record, rest } })),
    catchError(error => of({ error }))
  );
}

function accumulateObservation(state, outcome) {
  if (outcome.error) state.failure ??= outcome.error;
  else state.records.push(outcome.record);
  return state;
}

// Match raw identities before snapshot redaction can collapse distinct receive occurrences.
function matchClientDelivery(records, sessionId, envelopes) {
  const received = records.filter(
    entry => entry.sessionId === sessionId && entry.kind === 'websocket' && entry.websocket.direction === 'receive'
  );
  const occurrences = new Map();
  for (const entry of received) {
    const key = stableJson(JSON.parse(entry.websocket.payloadText));
    const queue = occurrences.get(key) ?? [];
    queue.push(entry.sequence);
    occurrences.set(key, queue);
  }
  const sequences = envelopes.map(envelope => {
    const sequence = occurrences.get(stableJson(envelope))?.shift();
    if (!sequence) throw new Error('Client delivery has no matching upstream receive occurrence');
    return sequence;
  });
  if (sequences.length !== received.length) throw new Error('Client delivery observation lost upstream frames');
  return sequences;
}

class LifecycleRecorder {
  #metadata;
  #sessions = new Map();
  #installations$ = new Subject();
  #explicit$ = new Subject();
  #stop$ = new Subject();
  #status = 'recording';
  #stopPromise;
  #captureState = { records: [], failure: undefined };
  #settled;

  constructor(metadata) {
    const errors = [];
    validateFixtureMetadata(errors, metadata);
    if (errors.length) throw new Error(errors.join('\n'));
    this.#metadata = metadata;
    this.#settled = this.#capture();
  }

  #capture() {
    const observations$ = merge(
      this.#installations$.pipe(mergeMap(({ page, session }) => observePage(page, session))),
      this.#explicit$
    );
    return lastValueFrom(
      observations$.pipe(
        // Stop listening immediately, then settle every body read already observed.
        takeUntil(this.#stop$),
        // The index is assigned before asynchronous body reads can change completion order.
        mergeMap(settleObservation),
        scan(accumulateObservation, this.#captureState)
      ),
      { defaultValue: this.#captureState }
    );
  }

  #requireRecording() {
    if (this.#status !== 'recording')
      throw new Error('Recorder cannot be restarted or accept new observations after stop');
  }

  install(page, sessionId) {
    this.#requireRecording();
    if (typeof sessionId !== 'string' || sessionId.length === 0)
      throw new Error('Recorder requires a non-empty session ID');
    if (this.#sessions.has(sessionId)) throw new Error('Recorder requires a new session ID');
    const session = { id: sessionId, outstandingRequests: new Set(), activeConnection: undefined };
    this.#sessions.set(sessionId, session);
    this.#installations$.next({ page, session });
  }

  context(sessionId, context) {
    this.#requireRecording();
    if (!this.#sessions.has(sessionId)) throw new Error('Unknown session');
    this.#explicit$.next({ entry: { kind: 'context', sessionId, context: { ...context } } });
  }

  droppedSend(sessionId, payloadText) {
    this.#requireRecording();
    const connectionId = this.#sessions.get(sessionId)?.activeConnection;
    if (!connectionId) throw new Error('Dropped-send observation requires an active captured socket');
    if (typeof payloadText !== 'string') throw new Error('Dropped-send observation requires a text payload');
    this.#explicit$.next({
      entry: {
        kind: 'websocket',
        sessionId,
        connectionId,
        delivery: 'dropped-before-server',
        websocket: { direction: 'send', payloadText }
      }
    });
  }

  clientDelivery(sessionId, envelopes) {
    if (this.#status !== 'stopped') throw new Error('Client delivery requires a stopped recorder and known session');
    if (!this.#sessions.has(sessionId))
      throw new Error('Client delivery requires a stopped recorder and known session');
    return matchClientDelivery(this.#captureState.records, sessionId, envelopes);
  }

  snapshot() {
    return {
      ...sanitizeFixture({
        version: lifecycleFixtureVersion,
        metadata: this.#metadata,
        records: [...this.#captureState.records].sort((left, right) => left.sequence - right.sequence)
      }),
      sessions: [...this.#sessions.keys()].map(id => ({ id }))
    };
  }

  stop() {
    if (!this.#stopPromise) {
      this.#status = 'stopping';
      this.#stop$.next();
      this.#stop$.complete();
      this.#stopPromise = this.#finish();
    }
    return this.#stopPromise;
  }

  async #finish() {
    const result = await this.#settled;
    if (result.failure) throw result.failure;
    for (const session of this.#sessions.values()) {
      if (session.outstandingRequests.size) throw new Error('unfinished notebook REST capture');
    }
    const errors = validateLifecycleFixture(this.snapshot());
    if (errors.length) throw new Error(errors.join('\n'));
    this.#status = 'stopped';
  }

  async write(file) {
    await this.stop();
    const fixture = this.snapshot();
    mkdirSync(path.dirname(file), { recursive: true });
    writeFileSync(file, `${JSON.stringify(fixture, null, 2)}\n`);
    return fixture;
  }
}

export function createLifecycleRecorder(metadata) {
  return new LifecycleRecorder(metadata);
}
