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
import { Subject, Subscription, fromEvent, ignoreElements, lastValueFrom, mergeMap, tap } from 'rxjs';
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

export function createLifecycleRecorder(metadata) {
  const metadataErrors = [];
  validateFixtureMetadata(metadataErrors, metadata);
  if (metadataErrors.length) {
    throw new Error(metadataErrors.join('\n'));
  }

  const records = [];
  const sessions = [];
  const bodyReads = new Subject();
  const subscriptions = new Subscription();
  const outstandingRequests = new Set();
  const activeConnections = new Map();
  let failure;
  let status = 'recording';
  let stopPromise;

  const requireRecording = () => {
    if (status !== 'recording') throw new Error('Recorder cannot be restarted or accept new observations after stop');
  };

  const record = entry => {
    const value = { ...entry, sequence: records.length + 1 };
    records.push(value);
    return value;
  };

  const bodyReadsFinished = lastValueFrom(
    bodyReads.pipe(
      mergeMap(read =>
        read().catch(error => {
          failure ??= error;
        })
      ),
      ignoreElements()
    ),
    { defaultValue: undefined }
  );

  const subscribe = (target, event, handler) => {
    subscriptions.add(
      fromEvent(target, event)
        .pipe(tap(handler))
        .subscribe({
          error: error => {
            failure ??= error;
          }
        })
    );
  };

  const captureRest = (page, sessionId) => {
    let requestNumber = 0;
    const requests = new Map();
    subscribe(page, 'request', request => {
      if (!isNotebookRestUrl(request.url())) return;
      const requestId = `${sessionId}:request:${++requestNumber}`;
      requests.set(request, requestId);
      outstandingRequests.add(request);
      record({
        kind: 'rest',
        sessionId,
        requestId,
        rest: { direction: 'request', request: captureRestRequest(request) }
      });
    });
    subscribe(page, 'requestfinished', request => {
      if (!requests.has(request)) return;
      const entry = record({ kind: 'rest', sessionId, requestId: requests.get(request) });
      bodyReads.next(async () => {
        const response = await request.response();
        const headers = Object.fromEntries(
          Object.entries(response.headers()).filter(([name]) => ['accept', 'content-type'].includes(name.toLowerCase()))
        );
        entry.rest = {
          direction: 'response',
          request: captureRestRequest(request),
          status: response.status(),
          headers,
          ...parseRestBody(await response.text(), headers)
        };
        outstandingRequests.delete(request);
      });
    });
    subscribe(page, 'requestfailed', request => {
      if (requests.has(request)) failure ??= new Error('Notebook REST request failed during capture');
    });
  };

  const captureSockets = (page, sessionId) => {
    let connectionNumber = 0;
    subscribe(page, 'websocket', socket => {
      if (!notebookSocket(socket.url())) return;
      const connectionId = `${sessionId}:socket:${++connectionNumber}`;
      activeConnections.set(sessionId, connectionId);
      record({ kind: 'connection', event: 'open', sessionId, connectionId });
      for (const [event, direction] of [
        ['framesent', 'send'],
        ['framereceived', 'receive']
      ]) {
        subscribe(socket, event, frame => {
          if (typeof frame.payload !== 'string') {
            failure ??= new Error('Binary lifecycle capture has no redaction policy');
            return;
          }
          record({
            kind: 'websocket',
            sessionId,
            connectionId,
            websocket: { direction, payloadText: frame.payload }
          });
        });
      }
      subscribe(socket, 'close', () => {
        activeConnections.delete(sessionId);
        record({ kind: 'connection', event: 'close', sessionId, connectionId });
      });
      subscribe(socket, 'socketerror', error => {
        failure ??= new Error(`Notebook socket error: ${error}`);
      });
    });
  };

  const snapshot = () => ({
    ...sanitizeFixture({ version: lifecycleFixtureVersion, metadata, records }),
    sessions: [...sessions]
  });
  return {
    install(page, sessionId) {
      requireRecording();
      if (typeof sessionId !== 'string' || sessionId.length === 0)
        throw new Error('Recorder requires a non-empty session ID');
      if (sessions.some(session => session.id === sessionId)) throw new Error('Recorder requires a new session ID');
      sessions.push({ id: sessionId });
      captureRest(page, sessionId);
      captureSockets(page, sessionId);
    },
    context(sessionId, context) {
      requireRecording();
      if (!sessions.some(session => session.id === sessionId)) throw new Error('Unknown session');
      record({ kind: 'context', sessionId, context: { ...context } });
    },
    droppedSend(sessionId, payloadText) {
      requireRecording();
      const connectionId = activeConnections.get(sessionId);
      if (!connectionId) {
        throw new Error('Dropped-send observation requires an active captured socket');
      }
      if (typeof payloadText !== 'string') throw new Error('Dropped-send observation requires a text payload');

      record({
        kind: 'websocket',
        sessionId,
        connectionId,
        delivery: 'dropped-before-server',
        websocket: { direction: 'send', payloadText }
      });
    },
    clientDelivery(sessionId, envelopes) {
      if (status !== 'stopped') {
        throw new Error('Client delivery requires a stopped recorder and known session');
      }
      if (!sessions.some(session => session.id === sessionId)) {
        throw new Error('Client delivery requires a stopped recorder and known session');
      }
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
    },
    snapshot,
    stop() {
      if (stopPromise) return stopPromise;
      status = 'stopping';
      stopPromise = (async () => {
        // Stop observing events; mergeMap completes after every already observed body read settles.
        subscriptions.unsubscribe();
        bodyReads.complete();
        await bodyReadsFinished;
        if (failure) throw failure;
        if (outstandingRequests.size) throw new Error('unfinished notebook REST capture');
        const errors = validateLifecycleFixture(snapshot());
        if (errors.length) {
          throw new Error(errors.join('\n'));
        }
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
