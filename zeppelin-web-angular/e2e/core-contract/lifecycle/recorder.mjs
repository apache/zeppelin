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

const notebookSocket = url => new URL(url).pathname === '/ws';

// Project Playwright observations into wire records. The capture pipeline owns ordering and settlement.
function observePage(page, sessionId, outstandingRequests, activeConnections) {
  const requests = new Map();
  let requestNumber = 0;
  let connectionNumber = 0;
  const events = (target, event) => fromEvent(target, event);
  const restRequests = events(page, 'request').pipe(
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
  const restResponses = events(page, 'requestfinished').pipe(
    filter(request => requests.has(request)),
    map(request => ({
      entry: { kind: 'rest', sessionId, requestId: requests.get(request) },
      readBody: async () => {
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
    }))
  );
  const restFailures = events(page, 'requestfailed').pipe(
    filter(request => requests.has(request)),
    map(() => {
      throw new Error('Notebook REST request failed during capture');
    })
  );
  const sockets = events(page, 'websocket').pipe(
    filter(socket => notebookSocket(socket.url())),
    mergeMap(socket => {
      const connectionId = `${sessionId}:socket:${++connectionNumber}`;
      activeConnections.set(sessionId, connectionId);
      const frames = direction =>
        map(frame => {
          if (typeof frame.payload !== 'string') throw new Error('Binary lifecycle capture has no redaction policy');
          return {
            entry: { kind: 'websocket', sessionId, connectionId, websocket: { direction, payloadText: frame.payload } }
          };
        });
      return merge(
        events(socket, 'framesent').pipe(frames('send')),
        events(socket, 'framereceived').pipe(frames('receive')),
        events(socket, 'close').pipe(
          map(() => {
            activeConnections.delete(sessionId);
            return { entry: { kind: 'connection', event: 'close', sessionId, connectionId } };
          })
        ),
        events(socket, 'socketerror').pipe(
          map(error => {
            throw new Error(`Notebook socket error: ${error}`);
          })
        )
      ).pipe(
        startWith({ entry: { kind: 'connection', event: 'open', sessionId, connectionId } }),
        takeWhile(observation => observation.entry.event !== 'close', true)
      );
    })
  );
  return merge(restRequests, restResponses, restFailures, sockets).pipe(catchError(error => of({ error })));
}

export function createLifecycleRecorder(metadata) {
  const metadataErrors = [];
  validateFixtureMetadata(metadataErrors, metadata);
  if (metadataErrors.length) throw new Error(metadataErrors.join('\n'));

  const sessions = [];
  const installations$ = new Subject();
  const explicit$ = new Subject();
  const stop$ = new Subject();
  const outstandingRequests = new Set();
  const activeConnections = new Map();
  let status = 'recording';
  let stopPromise;
  const captureState = { records: [], failure: undefined };

  const observations$ = merge(
    installations$.pipe(
      mergeMap(({ page, sessionId }) => observePage(page, sessionId, outstandingRequests, activeConnections))
    ),
    explicit$
  );
  const settled = lastValueFrom(
    observations$.pipe(
      takeUntil(stop$),
      // RxJS assigns the observation index before body reads can change completion order.
      map((observation, index) => ({ ...observation, sequence: index + 1 })),
      mergeMap(({ entry, readBody, error, sequence }) => {
        if (error) return of({ error });
        const record = { ...entry, sequence };
        return readBody
          ? defer(readBody).pipe(
              map(rest => ({ record: { ...record, rest } })),
              catchError(error => of({ error }))
            )
          : of({ record });
      }),
      // A single private accumulator; public snapshots copy and redact the completed records.
      scan((state, outcome) => {
        if (outcome.error) state.failure ??= outcome.error;
        else state.records.push(outcome.record);
        return state;
      }, captureState)
    ),
    { defaultValue: captureState }
  );

  const requireRecording = () => {
    if (status !== 'recording') throw new Error('Recorder cannot be restarted or accept new observations after stop');
  };
  const snapshot = () => ({
    ...sanitizeFixture({
      version: lifecycleFixtureVersion,
      metadata,
      records: [...captureState.records].sort((left, right) => left.sequence - right.sequence)
    }),
    sessions: [...sessions]
  });

  return {
    install(page, sessionId) {
      requireRecording();
      if (typeof sessionId !== 'string' || sessionId.length === 0)
        throw new Error('Recorder requires a non-empty session ID');
      if (sessions.some(session => session.id === sessionId)) throw new Error('Recorder requires a new session ID');
      sessions.push({ id: sessionId });
      installations$.next({ page, sessionId });
    },
    context(sessionId, context) {
      requireRecording();
      if (!sessions.some(session => session.id === sessionId)) throw new Error('Unknown session');
      explicit$.next({ entry: { kind: 'context', sessionId, context: { ...context } } });
    },
    droppedSend(sessionId, payloadText) {
      requireRecording();
      const connectionId = activeConnections.get(sessionId);
      if (!connectionId) throw new Error('Dropped-send observation requires an active captured socket');
      if (typeof payloadText !== 'string') throw new Error('Dropped-send observation requires a text payload');
      explicit$.next({
        entry: {
          kind: 'websocket',
          sessionId,
          connectionId,
          delivery: 'dropped-before-server',
          websocket: { direction: 'send', payloadText }
        }
      });
    },
    clientDelivery(sessionId, envelopes) {
      if (status !== 'stopped') {
        throw new Error('Client delivery requires a stopped recorder and known session');
      }
      if (!sessions.some(session => session.id === sessionId)) {
        throw new Error('Client delivery requires a stopped recorder and known session');
      }
      const received = captureState.records.filter(
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
    },
    snapshot,
    stop() {
      if (stopPromise) return stopPromise;
      status = 'stopping';
      stop$.next();
      stop$.complete();
      stopPromise = (async () => {
        const result = await settled;
        if (result.failure) throw result.failure;
        if (outstandingRequests.size) throw new Error('unfinished notebook REST capture');
        const errors = validateLifecycleFixture(snapshot());
        if (errors.length) throw new Error(errors.join('\n'));
        status = 'stopped';
      })();
      return stopPromise;
    },
    async write(file) {
      await this.stop();
      const fixture = snapshot();
      mkdirSync(path.dirname(file), { recursive: true });
      writeFileSync(file, `${JSON.stringify(fixture, null, 2)}\n`);
      return fixture;
    }
  };
}
