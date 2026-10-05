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

import { EMPTY, Subject, concatMap, defer, from } from 'rxjs';
import { createLifecycleDeliveryScheduler } from './delivery-scheduler.mjs';
import { isNotebookRestUrl, captureRestRequest, stableJson, webSocketPayloadMatches } from '../transport/fixture.mjs';
import { validateLifecycleFixture } from './validation.mjs';

const notebookSocket = url => new URL(url).pathname === '/ws';

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
      ids.add(envelope.msgId);
    }
  }

  const remoteIds = new Set([...receivedIds].filter(id => !sentIds.has(id)));

  return {
    matchSend(record, payload) {
      const expected = JSON.parse(record.websocket.payloadText);
      const actual = JSON.parse(String(payload));
      const idKey = expected.msgId;
      const liveKey = actual.msgId;
      if (expected.msgId === null && actual.msgId !== null) {
        throw new Error('Lifecycle msgId correlation mismatch');
      }
      if (typeof expected.msgId === 'string') {
        if (typeof liveKey !== 'string' || liveKey.length === 0) {
          throw new Error('Lifecycle msgId correlation mismatch: runtime ID must be a non-empty string');
        }
        if (remoteIds.has(liveKey)) {
          throw new Error('Lifecycle msgId correlation mismatch: runtime ID collides with a remote ID');
        }
        if (recordedIds.has(idKey) && recordedIds.get(idKey) !== liveKey) {
          throw new Error('Lifecycle msgId correlation mismatch: recorded ID already has a different binding');
        }
        if (runtimeIds.has(liveKey) && runtimeIds.get(liveKey) !== idKey) {
          throw new Error('Lifecycle msgId correlation mismatch: runtime ID already belongs to another recorded ID');
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
      const binding = recordedIds.get(envelope.msgId);
      if (typeof envelope.msgId === 'string' && !binding) {
        if (runtimeIds.has(envelope.msgId) && runtimeIds.get(envelope.msgId) !== envelope.msgId) {
          throw new Error('Lifecycle msgId correlation mismatch');
        }
        recordedIds.set(envelope.msgId, envelope.msgId);
        runtimeIds.set(envelope.msgId, envelope.msgId);
      }
      return binding ? JSON.stringify({ ...envelope, msgId: binding }) : record.websocket.payloadText;
    }
  };
}

function createRestReplayQueue() {
  const waiting = [];
  const delivering = new Set();

  return {
    enqueue(route, sessionId, shape) {
      return new Promise((resolve, reject) => waiting.push({ route, sessionId, shape, resolve, reject }));
    },
    match(record) {
      const request = waiting.find(entry => {
        if (entry.requestId !== undefined) return false;
        if (entry.sessionId !== record.sessionId) return false;
        return matchesRequest(record.rest.request, entry.shape);
      });
      if (!request) return false;
      request.requestId = record.requestId;
      return true;
    },
    takeResponse(record) {
      const index = waiting.findIndex(
        entry => entry.sessionId === record.sessionId && entry.requestId === record.requestId
      );
      if (index < 0) return null;
      const request = waiting.splice(index, 1)[0];
      delivering.add(request);
      return request;
    },
    async respond(request, response) {
      try {
        await request.route.fulfill({
          status: response.status,
          headers: response.headers,
          body: response.bodyRaw ?? JSON.stringify(response.bodyJson)
        });
        request.resolve();
      } catch (error) {
        request.reject(error);
        throw error;
      } finally {
        delivering.delete(request);
      }
    },
    abort(error) {
      waiting.splice(0).forEach(request => request.reject(error));
      delivering.forEach(request => request.reject(error));
    },
    pending() {
      return { requests: waiting.length, responses: delivering.size };
    }
  };
}

// Faults change delivery only. Captured records, payloads and sequence numbers stay immutable.
export function createLifecycleReplay(fixture, faults = []) {
  const errors = validateLifecycleFixture(fixture);
  if (errors.length) {
    throw new Error(errors.join('\n'));
  }
  const sockets = new Map();
  const connectingSockets = new Map();
  const installedSessions = new Set();
  const restQueue = createRestReplayQueue();

  const correlation = createMessageCorrelation(fixture);
  let cursor = 0;
  let fatalError;
  const drainRequests = new Subject();
  let drainSubscription;

  const abortPending = error => {
    fatalError ??= error;
    restQueue.abort(fatalError);
    deliveries.dispose();
    drainSubscription?.unsubscribe();
    drainRequests.complete();
  };

  const fail = error => {
    abortPending(error);
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

  const deliveries = createLifecycleDeliveryScheduler(fixture, faults, deliver, abortPending);

  const advance = () => {
    cursor++;
    deliveries.flush(cursor);
  };

  const consumeConnection = record => {
    if (record.event === 'close') {
      sockets.get(record.connectionId).close({ code: 1012, reason: 'Recorded disconnect' });
      sockets.delete(record.connectionId);
      return true;
    }
    const socket = connectingSockets.get(record.sessionId);
    if (!socket) return false;
    connectingSockets.delete(record.sessionId);
    sockets.set(record.connectionId, socket);
    return true;
  };

  const consumeAvailable = record => {
    switch (record.kind) {
      case 'connection':
        return consumeConnection(record);
      case 'websocket':
        if (record.websocket.direction === 'send') return false;
        deliveries.enqueue(record);
        return true;
      case 'rest':
        return restQueue.match(record);
      case 'context':
        return false;
    }
  };

  const drain = () => {
    while (cursor < fixture.records.length) {
      if (fatalError) throw fatalError;
      const record = fixture.records[cursor];
      if (record.kind === 'rest' && record.rest.direction === 'response') {
        const response = restQueue.takeResponse(record);
        if (!response) break;
        // Advance before fulfillment so the next input may cross this response barrier.
        advance();
        return from(restQueue.respond(response, record.rest)).pipe(concatMap(() => defer(drain)));
      }

      if (!consumeAvailable(record)) break;
      advance();
    }
    return EMPTY;
  };

  drainSubscription = drainRequests.pipe(concatMap(() => defer(drain))).subscribe({ error: abortPending });
  const scheduleDrain = () => drainRequests.next();

  return {
    async install(page, sessionId) {
      if (fatalError) {
        throw fatalError;
      }
      if (installedSessions.has(sessionId)) throw new Error('Replay session is already installed');
      if (!fixture.sessions.some(session => session.id === sessionId))
        throw new Error('Replay requires a recorded session');
      installedSessions.add(sessionId);
      await page.route('**/api/**', async (route, request) => {
        if (!isNotebookRestUrl(request.url())) {
          return route.fallback();
        }
        if (fatalError) {
          throw fatalError;
        }
        const shape = captureRestRequest(request);
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
        const response = restQueue.enqueue(route, sessionId, shape);
        scheduleDrain();
        await response;
      });
      await page.routeWebSocket(notebookSocket, socket => {
        const connectionBatch = consecutiveRecords(
          fixture.records,
          cursor,
          record => record.kind === 'connection' && record.event === 'open'
        );

        if (fatalError) return fail(fatalError);
        if (connectingSockets.has(sessionId)) {
          return fail(new Error('Unexpected lifecycle WebSocket connection: session already connecting'));
        }
        if (!connectionBatch.some(record => record.sessionId === sessionId)) {
          return fail(new Error('Unexpected lifecycle WebSocket connection'));
        }
        connectingSockets.set(sessionId, socket);
        socket.onMessage(payload => {
          const record = fixture.records[cursor];
          if (fatalError) return fail(fatalError);
          if (record?.kind !== 'websocket') {
            return fail(new Error('Lifecycle WebSocket send out of order: expected another record kind'));
          }
          if (record.websocket.direction !== 'send') {
            return fail(new Error('Lifecycle WebSocket send out of order: expected a receive'));
          }
          const ownsSocket = record.sessionId === sessionId && sockets.get(record.connectionId) === socket;
          if (!ownsSocket) {
            return fail(new Error('Lifecycle WebSocket send out of order'));
          }
          try {
            correlation.matchSend(record, payload);
          } catch (error) {
            return fail(error);
          }
          advance();
          scheduleDrain();
        });
        scheduleDrain();
      });
    },
    context(sessionId, context) {
      if (fatalError) return fail(fatalError);
      const record = fixture.records[cursor];
      if (record?.kind !== 'context')
        return fail(new Error('Lifecycle route context out of order: expected another record kind'));
      if (record.sessionId !== sessionId) return fail(new Error('Lifecycle route context out of order: wrong session'));
      if (stableJson(record.context) !== stableJson(context))
        return fail(new Error('Lifecycle route context out of order: different route'));
      advance();
      scheduleDrain();
    },
    assertComplete() {
      if (!this.isComplete()) {
        const pending = restQueue.pending();
        throw new Error(
          `Lifecycle fixture has unconsumed records or pending deliveries: ${JSON.stringify({
            records: fixture.records.length - cursor,
            restRequests: pending.requests,
            restResponses: pending.responses,
            scheduledDelivery: deliveries.hasPending(),
            connectingSockets: connectingSockets.size
          })}`
        );
      }
    },
    isComplete() {
      if (fatalError) throw fatalError;
      if (cursor !== fixture.records.length) return false;
      const pending = restQueue.pending();
      if (pending.requests > 0) return false;
      if (pending.responses > 0) return false;
      if (deliveries.hasPending()) return false;
      return connectingSockets.size === 0;
    },
    position() {
      if (fatalError) throw fatalError;
      return cursor;
    },
    dispose() {
      abortPending(new Error('Lifecycle replay disposed'));
      sockets.forEach(socket => socket.close());
      sockets.clear();
      connectingSockets.forEach(socket => socket.close());
      connectingSockets.clear();
    }
  };
}
