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
import { createLifecycleDeliveryScheduler } from './lifecycle-delivery-scheduler.mjs';

import {
  isNotebookRestUrl,
  normalizeFixtureRecord,
  parseRestBody,
  sanitizeFixture,
  stableJson,
  summarizeRequest,
  validateFixtureMetadata,
  validateRestRecord,
  validateWebSocketRecord,
  webSocketPayloadMatches
} from './notebook-transport-fixture.mjs';

export const lifecycleFixtureVersion = 2;

const notebookSocket = url => new URL(url).pathname === '/ws';
const requestShape = request => normalizeFixtureRecord(summarizeRequest(request));

const matchesRequest = (expected, actual) => {
  const normalize = value => ({ ...value, headers: { accept: '*/*', ...value.headers } });
  return stableJson(normalize(expected)) === stableJson(normalize(actual));
};

function consecutiveRecords(records, cursor, predicate) {
  const batch = [];

  for (const record of records.slice(cursor)) {
    if (!predicate(record)) {
      break;
    }
    batch.push(record);
  }

  return batch;
}

function createMessageCorrelation(fixture) {
  const recordedIds = new Map();
  const runtimeIds = new Map();
  const sentIds = new Set();
  const receivedIds = new Set();

  for (const record of fixture.records) {
    if (record.kind !== 'websocket') {
      continue;
    }
    const envelope = JSON.parse(record.websocket.payloadText);
    if (typeof envelope.msgId === 'string') {
      const ids = record.websocket.direction === 'send' ? sentIds : receivedIds;
      ids.add(`${record.sessionId}:${envelope.msgId}`);
    }
  }

  const remoteIds = new Set([...receivedIds].filter(id => !sentIds.has(id)));

  return {
    matchSend(record, payload) {
      const sessionId = record.sessionId;
      const expected = JSON.parse(record.websocket.payloadText);
      const actual = JSON.parse(String(payload));
      const idKey = `${sessionId}:${expected.msgId}`;
      const liveKey = `${sessionId}:${actual.msgId}`;
      if (typeof expected.msgId === 'string') {
        if (
          typeof actual.msgId !== 'string' ||
          !actual.msgId ||
          remoteIds.has(liveKey) ||
          (recordedIds.has(idKey) && recordedIds.get(idKey) !== actual.msgId) ||
          (runtimeIds.has(liveKey) && runtimeIds.get(liveKey) !== idKey)
        ) {
          throw new Error('Lifecycle msgId correlation mismatch');
        }
        expected.msgId = actual.msgId;
      }
      if (!webSocketPayloadMatches(JSON.stringify(expected), payload)) {
        throw new Error('Lifecycle frame mismatch');
      }
      if (typeof expected.msgId === 'string') {
        recordedIds.set(idKey, actual.msgId);
        runtimeIds.set(liveKey, idKey);
      }
    },
    replyPayload(record) {
      const envelope = JSON.parse(record.websocket.payloadText);
      const binding = recordedIds.get(`${record.sessionId}:${envelope.msgId}`);
      return binding ? JSON.stringify({ ...envelope, msgId: binding }) : record.websocket.payloadText;
    }
  };
}
function validateConnection(errors, prefix, record, state) {
  const { sessionId, connectionId, event } = record;

  if (typeof connectionId !== 'string' || !connectionId) {
    errors.push(`${prefix}: connectionId required`);
  }

  if (event === 'open') {
    if (state.connections.has(connectionId) || state.activeConnections.has(sessionId)) {
      errors.push(`${prefix}: duplicate or overlapping connection`);
    }

    state.connections.add(connectionId);
    state.activeConnections.set(sessionId, connectionId);
    return;
  }

  if (event === 'close') {
    if (state.activeConnections.get(sessionId) !== connectionId) {
      errors.push(`${prefix}: closing inactive connection`);
    }

    state.activeConnections.delete(sessionId);
    return;
  }

  errors.push(`${prefix}: unknown connection event`);
}

function validateRouteContext(errors, prefix, record) {
  const context = record.context;

  if (
    !context ||
    !['active', 'inactive'].includes(context.state) ||
    typeof context.noteId !== 'string' ||
    !context.noteId ||
    !(context.revisionId === null || (typeof context.revisionId === 'string' && context.revisionId))
  ) {
    errors.push(`${prefix}: invalid route context`);
  }
}

function validateLifecycleRest(errors, prefix, record, state) {
  validateRestRecord(errors, prefix, record);

  if (typeof record.requestId !== 'string' || !record.requestId) {
    errors.push(`${prefix}: requestId required`);
  }

  const requestId = `${record.sessionId}:${record.requestId}`;
  const rest = record.rest;

  if (rest?.direction === 'request') {
    if (state.seenRequests.has(requestId)) {
      errors.push(`${prefix}: duplicate REST request`);
    }

    state.seenRequests.add(requestId);
    state.requests.set(requestId, rest.request);
  } else if (rest?.direction === 'response') {
    if (!Number.isInteger(rest.status) || rest.status < 100 || rest.status > 599) {
      errors.push(`${prefix}: response status required`);
    }

    if (!state.requests.has(requestId) || stableJson(state.requests.get(requestId)) !== stableJson(rest.request)) {
      errors.push(`${prefix}: unmatched REST response`);
    }

    state.requests.delete(requestId);
  }
}

function validateLifecycleFrame(errors, prefix, record, state) {
  validateWebSocketRecord(errors, prefix, record);

  if (
    record.delivery !== undefined &&
    (record.delivery !== 'dropped-before-server' || record.websocket?.direction !== 'send')
  ) {
    errors.push(`${prefix}: invalid dropped-send observation`);
  }

  if (!record.connectionId || state.activeConnections.get(record.sessionId) !== record.connectionId) {
    errors.push(`${prefix}: frame on inactive connection`);
  }

  let envelope;
  try {
    envelope = JSON.parse(record.websocket?.payloadText);
  } catch {
    errors.push(`${prefix}: lifecycle replay requires a JSON envelope`);
    return;
  }

  if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope)) {
    errors.push(`${prefix}: lifecycle replay requires a JSON envelope`);
  } else if (
    envelope.msgId !== undefined &&
    envelope.msgId !== null &&
    (typeof envelope.msgId !== 'string' || !envelope.msgId || envelope.msgId === '<msgId>')
  ) {
    errors.push(`${prefix}: ambiguous or invalid msgId; recapture the fixture`);
  }
}

const recordValidators = {
  connection: validateConnection,
  context: validateRouteContext,
  rest: validateLifecycleRest,
  websocket: validateLifecycleFrame
};

// Context belongs to the capture, never to an untagged server frame.
export function validateLifecycleFixture(fixture) {
  const errors = [];

  if (fixture?.version !== lifecycleFixtureVersion) {
    errors.push('Unsupported lifecycle fixture version; recapture v1');
  }

  validateFixtureMetadata(errors, fixture?.metadata);

  if (!Array.isArray(fixture?.records) || !fixture.records.length) {
    return [...errors, 'records must be non-empty'];
  }

  if (!Array.isArray(fixture.sessions) || !fixture.sessions.length) {
    return [...errors, 'sessions must be non-empty'];
  }

  const sessions = new Set();
  for (const session of fixture.sessions) {
    if (typeof session?.id !== 'string' || !session.id || sessions.has(session.id)) {
      errors.push('session IDs must be unique non-empty strings');
    }

    sessions.add(session?.id);
  }

  const state = {
    connections: new Set(),
    activeConnections: new Map(),
    requests: new Map(),
    seenRequests: new Set()
  };

  for (const [index, record] of fixture.records.entries()) {
    const prefix = `records[${index}]`;

    if (record?.sequence !== index + 1) {
      errors.push(`${prefix}: contiguous sequence required; ordering or records lost`);
    }

    if (!sessions.has(record?.sessionId)) {
      errors.push(`${prefix}: unknown session`);
    }

    const validateRecord = recordValidators[record?.kind];
    if (Object.hasOwn(recordValidators, record?.kind)) {
      validateRecord(errors, prefix, record, state);
    } else {
      errors.push(`${prefix}: unknown record kind`);
    }
  }

  if (state.requests.size) {
    errors.push('unfinished REST requests');
  }

  return [...new Set(errors)];
}

function assertValidLifecycleFixture(fixture) {
  const errors = validateLifecycleFixture(fixture);
  if (errors.length) {
    throw new Error(errors.join('\n'));
  }

  return fixture;
}

export function createLifecycleRecorder(metadata) {
  const metadataErrors = [];
  validateFixtureMetadata(metadataErrors, metadata);
  if (metadataErrors.length) {
    throw new Error(metadataErrors.join('\n'));
  }

  const records = [];
  const sessions = [];
  const pendingBodyReads = new Set();
  const outstandingRequests = new Set();
  const eventSubscriptions = [];
  const activeConnections = new Map();
  let failure;
  let stopped = false;

  const record = entry => {
    const value = { ...entry, sequence: records.length + 1 };
    records.push(value);
    return value;
  };

  const subscribe = (target, event, handler) => {
    target.on(event, handler);
    eventSubscriptions.push([target, event, handler]);
  };

  const snapshot = () => ({
    ...sanitizeFixture({ version: lifecycleFixtureVersion, metadata, records }),
    sessions: [...sessions]
  });
  return {
    install(page, sessionId) {
      if (stopped || !sessionId || sessions.some(session => session.id === sessionId)) {
        throw new Error('Recorder requires a new session ID and cannot be restarted');
      }
      sessions.push({ id: sessionId });
      let connectionNumber = 0;
      let requestNumber = 0;
      const requests = new Map();
      subscribe(page, 'request', request => {
        if (!isNotebookRestUrl(request.url())) return;
        const requestId = `${sessionId}:request:${++requestNumber}`;
        requests.set(request, requestId);
        outstandingRequests.add(request);
        record({ kind: 'rest', sessionId, requestId, rest: { direction: 'request', request: requestShape(request) } });
      });
      subscribe(page, 'requestfinished', request => {
        if (!requests.has(request)) return;
        const entry = record({ kind: 'rest', sessionId, requestId: requests.get(request) });
        const completion = (async () => {
          const response = await request.response();
          const headers = Object.fromEntries(
            Object.entries(response.headers()).filter(([name]) =>
              ['accept', 'content-type'].includes(name.toLowerCase())
            )
          );
          entry.rest = {
            direction: 'response',
            request: requestShape(request),
            status: response.status(),
            headers,
            ...parseRestBody(await response.text(), headers)
          };
          outstandingRequests.delete(request);
        })().catch(error => {
          failure ??= error;
        });
        pendingBodyReads.add(completion);
        void completion.finally(() => pendingBodyReads.delete(completion));
      });
      subscribe(page, 'requestfailed', request => {
        if (requests.has(request)) failure ??= new Error('Notebook REST request failed during capture');
      });
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
    },
    context(sessionId, context) {
      if (stopped || !sessions.some(session => session.id === sessionId)) {
        throw new Error('Unknown or stopped session');
      }
      record({ kind: 'context', sessionId, context: { ...context } });
    },
    droppedSend(sessionId, payloadText) {
      const connectionId = activeConnections.get(sessionId);
      if (stopped || !connectionId || typeof payloadText !== 'string') {
        throw new Error('Dropped-send observation requires an active captured socket');
      }

      record({
        kind: 'websocket',
        sessionId,
        connectionId,
        delivery: 'dropped-before-server',
        websocket: { direction: 'send', payloadText }
      });
    },
    snapshot,
    async stop() {
      stopped = true;
      // Keep completion listeners until already observed responses have finished.
      const detach = ([target, event, handler]) => target.off(event, handler);
      for (const item of eventSubscriptions.filter(item => item[1] !== 'requestfinished')) detach(item);
      while (pendingBodyReads.size) await Promise.all([...pendingBodyReads]);
      for (const item of eventSubscriptions.filter(item => item[1] === 'requestfinished')) detach(item);
      if (failure) {
        throw failure;
      }
      if (outstandingRequests.size) {
        throw new Error('unfinished notebook REST capture');
      }
      assertValidLifecycleFixture(snapshot());
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

// Faults change delivery only. Captured records, payloads and sequence numbers stay immutable.
export function createLifecycleReplay(fixture, faults = []) {
  assertValidLifecycleFixture(fixture);
  const sockets = new Map();
  const connectingSockets = new Map();
  const installedSessions = new Set();
  const pendingRestRequests = [];
  const inFlightRestDeliveries = new Set();

  const correlation = createMessageCorrelation(fixture);
  let cursor = 0;
  let fatalError;
  let draining = false;
  let drainRequested = false;

  const fail = error => {
    fatalError ??= error;
    pendingRestRequests.splice(0).forEach(pending => pending.reject(fatalError));
    throw fatalError;
  };

  const deliver = (record, copies) => {
    if (!copies) return;
    const socket = sockets.get(record.connectionId);
    if (!socket) {
      return fail(new Error('Delayed frame targets a closed connection'));
    }
    const payload = correlation.replyPayload(record);
    for (let copy = 0; copy < copies; copy++) socket.send(payload);
  };

  const deliveries = createLifecycleDeliveryScheduler(fixture, faults, deliver, error => {
    fatalError ??= error;
  });

  const drain = async () => {
    if (draining) {
      drainRequested = true;
      return;
    }
    draining = true;
    try {
      do {
        drainRequested = false;
        while (cursor < fixture.records.length) {
          const record = fixture.records[cursor];
          if (record.kind === 'context') {
            break;
          }
          if (record.kind === 'connection') {
            if (record.event === 'open') {
              const socket = connectingSockets.get(record.sessionId);
              if (!socket) {
                break;
              }
              connectingSockets.delete(record.sessionId);
              sockets.set(record.connectionId, socket);
            } else {
              sockets.get(record.connectionId).close({ code: 1012, reason: 'Recorded disconnect' });
              sockets.delete(record.connectionId);
            }
          } else if (record.kind === 'websocket') {
            if (record.websocket.direction === 'send') {
              break;
            }
            deliveries.enqueue(record);
          } else if (record.rest.direction === 'request') {
            const pending = pendingRestRequests.find(
              entry =>
                !entry.matched &&
                entry.sessionId === record.sessionId &&
                matchesRequest(record.rest.request, entry.shape)
            );
            if (!pending) {
              break;
            }
            pending.matched = record.requestId;
          } else {
            const index = pendingRestRequests.findIndex(
              entry => entry.sessionId === record.sessionId && entry.matched === record.requestId
            );
            if (index < 0) {
              break;
            }
            const pending = pendingRestRequests.splice(index, 1)[0];
            inFlightRestDeliveries.add(pending);
            cursor++;
            deliveries.flush(cursor);

            try {
              await pending.route.fulfill({
                status: record.rest.status,
                headers: record.rest.headers,
                body: record.rest.bodyRaw ?? JSON.stringify(record.rest.bodyJson)
              });
              pending.resolve();
            } catch (error) {
              pending.reject(error);
              throw error;
            } finally {
              inFlightRestDeliveries.delete(pending);
            }
            continue;
          }
          cursor++;
          deliveries.flush(cursor);
        }
      } while (drainRequested);
    } catch (error) {
      fail(error);
    } finally {
      draining = false;
    }
  };

  const scheduleDrain = () => {
    void drain().catch(error => {
      fatalError ??= error;
    });
  };

  return {
    async install(page, sessionId) {
      if (fatalError) {
        throw fatalError;
      }
      if (installedSessions.has(sessionId) || !fixture.sessions.some(session => session.id === sessionId)) {
        throw new Error('Replay requires a distinct recorded session');
      }
      installedSessions.add(sessionId);
      await page.route('**/api/**', async (route, request) => {
        if (!isNotebookRestUrl(request.url())) {
          return route.fallback();
        }
        if (fatalError) {
          throw fatalError;
        }
        const shape = requestShape(request);
        // Only consecutive requests may arrive in a different order.
        const requestBatch = consecutiveRecords(
          fixture.records,
          cursor,
          record => record.kind === 'rest' && record.rest.direction === 'request'
        );

        if (
          !requestBatch.some(record => record.sessionId === sessionId && matchesRequest(record.rest.request, shape))
        ) {
          return fail(new Error(`Unexpected lifecycle REST request: ${stableJson(shape)}`));
        }
        await new Promise((resolve, reject) => {
          pendingRestRequests.push({ route, sessionId, shape, resolve, reject });
          scheduleDrain();
        });
      });
      await page.routeWebSocket(notebookSocket, socket => {
        const connectionBatch = consecutiveRecords(
          fixture.records,
          cursor,
          record => record.kind === 'connection' && record.event === 'open'
        );

        if (
          fatalError ||
          connectingSockets.has(sessionId) ||
          !connectionBatch.some(record => record.sessionId === sessionId)
        ) {
          return fail(new Error('Unexpected lifecycle WebSocket connection'));
        }
        connectingSockets.set(sessionId, socket);
        socket.onMessage(payload => {
          const record = fixture.records[cursor];
          if (
            record?.kind !== 'websocket' ||
            record.websocket.direction !== 'send' ||
            sockets.get(record.connectionId) !== socket ||
            record.sessionId !== sessionId
          ) {
            return fail(new Error('Lifecycle WebSocket send out of order'));
          }
          try {
            correlation.matchSend(record, payload);
          } catch (error) {
            return fail(error);
          }
          cursor++;
          deliveries.flush(cursor);
          scheduleDrain();
        });
        scheduleDrain();
      });
    },
    context(sessionId, context) {
      const record = fixture.records[cursor];
      if (
        record?.kind !== 'context' ||
        record.sessionId !== sessionId ||
        stableJson(record.context) !== stableJson(context)
      ) {
        return fail(new Error('Lifecycle route context out of order'));
      }
      cursor++;
      deliveries.flush(cursor);
      scheduleDrain();
    },
    assertComplete() {
      if (fatalError) {
        throw fatalError;
      }
      if (
        cursor !== fixture.records.length ||
        pendingRestRequests.length ||
        inFlightRestDeliveries.size ||
        deliveries.hasPending() ||
        connectingSockets.size
      ) {
        throw new Error('Lifecycle fixture has unconsumed records or pending deliveries');
      }
    },
    position() {
      return cursor;
    },
    dispose() {
      fatalError ??= new Error('Lifecycle replay disposed');
      pendingRestRequests.splice(0).forEach(pending => pending.reject(fatalError));
      inFlightRestDeliveries.forEach(pending => pending.reject(fatalError));
      deliveries.dispose();
      sockets.forEach(socket => socket.close());
      sockets.clear();
      connectingSockets.forEach(socket => socket.close());
      connectingSockets.clear();
    }
  };
}
