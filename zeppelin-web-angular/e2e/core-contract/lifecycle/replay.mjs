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

import {
  AsyncSubject,
  BehaviorSubject,
  EMPTY,
  ReplaySubject,
  Subject,
  combineLatest,
  concatMap,
  defer,
  filter,
  firstValueFrom,
  from,
  take,
  takeUntil,
  tap,
  timeout
} from 'rxjs';
import { createLifecycleDeliveryScheduler } from './delivery-scheduler.mjs';
import { isNotebookRestUrl, captureRestRequest, stableJson } from '../transport/fixture.mjs';
import {
  matchesRequest,
  consecutiveRecords,
  createMessageCorrelation,
  matchOutgoingFrame,
  incomingFrame,
  requiresRuntimeInput
} from './replay-plan.mjs';
import { validateLifecycleFixture } from './validation.mjs';

const notebookSocket = url => new URL(url).pathname === '/ws';

// Each recorded input has one bounded gate. Unmatched runtime traffic is rejected before entering the stream.
export function createLifecycleReplay(fixture, faults = []) {
  const errors = validateLifecycleFixture(fixture);
  if (errors.length) throw new Error(errors.join('\n'));

  const inputs = new Map(
    fixture.records.filter(requiresRuntimeInput).map(record => [record.sequence, new ReplaySubject(1)])
  );
  const sockets = new Map();
  const ownedSockets = new Set();
  const requests = new Map();
  const installedSessions = new Set();
  // Admission may lead effect execution: valid context/send inputs can arrive during REST fulfillment.
  // Automatic outputs remain serialized by the trace stream; this boundary only moves forward.
  const admitted$ = new BehaviorSubject(0);
  const stopped$ = new Subject();
  const traceDone$ = new BehaviorSubject(false);
  const correlation = createMessageCorrelation(fixture);
  let fatalError;

  const abort = error => {
    if (fatalError) return;
    fatalError = error;
    stopped$.error(error);
    admitted$.error(error);
    deliveries.dispose();
    inputs.clear();
    requests.clear();
  };
  const fail = error => {
    abort(error);
    throw fatalError;
  };
  const position = () => {
    if (fatalError) throw fatalError;
    return admitted$.value;
  };
  const publish = (record, value) => {
    position();
    const gate = inputs.get(record.sequence);
    if (gate.isStopped) return fail(new Error('Lifecycle input was already consumed'));
    gate.next(value);
    gate.complete();
    if (!fatalError && (record.kind === 'context' || record.kind === 'websocket')) advance(record);
  };
  const requestKey = record => `${record.sessionId}:${record.requestId}`;
  const bindMessageId = binding => {
    if (!binding) return;
    const [recorded, runtime] = binding;
    correlation.recorded.set(recorded, runtime);
    correlation.runtime.set(runtime, recorded);
  };
  const deliver = (record, copies) => {
    if (!copies) return;
    const socket = sockets.get(record.connectionId);
    if (!socket) throw new Error('Delayed frame targets a closed connection');
    const { binding, payload } = incomingFrame(record, correlation);
    bindMessageId(binding);
    for (let copy = 0; copy < copies; copy++) socket.send(payload);
  };
  const deliveries = createLifecycleDeliveryScheduler(fixture, faults, admitted$, deliver, abort);
  const advance = record => {
    const consumed = Math.max(admitted$.value, record.sequence);
    admitted$.next(consumed);
  };

  const execute = (record, input) => {
    inputs.delete(record.sequence);
    switch (record.kind) {
      case 'connection':
        if (record.event === 'open') sockets.set(record.connectionId, input);
        else {
          sockets.get(record.connectionId).close({ code: 1012, reason: 'Recorded disconnect' });
          ownedSockets.delete(sockets.get(record.connectionId));
          sockets.delete(record.connectionId);
        }
        break;
      case 'websocket':
        break;
      case 'rest':
        if (record.rest.direction === 'response') {
          const request = requests.get(requestKey(record));
          // The next input becomes admissible before fulfillment; later delivery still waits for it.
          advance(record);
          return defer(() =>
            request.route.fulfill({
              status: record.rest.status,
              headers: record.rest.headers,
              body: record.rest.bodyRaw ?? JSON.stringify(record.rest.bodyJson)
            })
          ).pipe(
            tap(() => {
              requests.delete(requestKey(record));
              request.completed.next();
              request.completed.complete();
            })
          );
        }
        break;
    }
    advance(record);
    return EMPTY;
  };

  from(fixture.records)
    .pipe(
      concatMap(record => {
        const gate = inputs.get(record.sequence);
        return gate
          ? gate.pipe(
              take(1),
              concatMap(input => execute(record, input))
            )
          : defer(() => execute(record));
      }),
      takeUntil(stopped$)
    )
    .subscribe({ error: abort, complete: () => traceDone$.next(true) });

  return {
    async install(page, sessionId) {
      position();
      if (installedSessions.has(sessionId)) throw new Error('Replay session is already installed');
      if (!fixture.sessions.some(session => session.id === sessionId))
        throw new Error('Replay requires a recorded session');
      installedSessions.add(sessionId);
      await page.route('**/api/**', async (route, request) => {
        if (!isNotebookRestUrl(request.url())) return route.fallback();
        const shape = captureRestRequest(request);
        const batch = consecutiveRecords(
          fixture.records,
          position(),
          record => record.kind === 'rest' && record.rest.direction === 'request'
        );
        const record = batch.find(
          record =>
            record.sessionId === sessionId &&
            !inputs.get(record.sequence).isStopped &&
            matchesRequest(record.rest.request, shape)
        );
        if (!record) return fail(new Error(`Unexpected lifecycle REST request: ${stableJson(shape)}`));
        const completed = new AsyncSubject();
        requests.set(requestKey(record), { route, completed });
        const response = firstValueFrom(completed.pipe(takeUntil(stopped$)));
        publish(record, request);
        await response;
      });
      await page.routeWebSocket(notebookSocket, socket => {
        const batch = consecutiveRecords(
          fixture.records,
          position(),
          record => record.kind === 'connection' && record.event === 'open'
        );
        const record = batch.find(record => record.sessionId === sessionId && !inputs.get(record.sequence).isStopped);
        if (!record) return fail(new Error('Unexpected lifecycle WebSocket connection'));
        ownedSockets.add(socket);
        socket.onMessage(payload => {
          const expected = fixture.records[position()];
          if (expected?.kind !== 'websocket')
            return fail(new Error('Lifecycle WebSocket send out of order: expected another record kind'));
          if (expected.websocket.direction !== 'send')
            return fail(new Error('Lifecycle WebSocket send out of order: expected a receive'));
          if (expected.sessionId !== sessionId || sockets.get(expected.connectionId) !== socket)
            return fail(new Error('Lifecycle WebSocket send out of order'));
          try {
            bindMessageId(matchOutgoingFrame(expected, payload, correlation));
          } catch (error) {
            return fail(error);
          }
          publish(expected, payload);
        });
        publish(record, socket);
      });
    },
    context(sessionId, context) {
      const record = fixture.records[position()];
      if (record?.kind !== 'context')
        return fail(new Error('Lifecycle route context out of order: expected another record kind'));
      if (record.sessionId !== sessionId) return fail(new Error('Lifecycle route context out of order: wrong session'));
      if (stableJson(record.context) !== stableJson(context))
        return fail(new Error('Lifecycle route context out of order: different route'));
      publish(record, context);
    },
    assertComplete() {
      if (!this.isComplete())
        throw new Error(
          `Lifecycle fixture has unconsumed records or pending deliveries: ${JSON.stringify({ records: fixture.records.length - position(), restRequests: requests.size, scheduledDelivery: deliveries.hasPending() })}`
        );
    },
    isComplete() {
      position();
      return traceDone$.value && !deliveries.hasPending();
    },
    position,
    waitForComplete() {
      return firstValueFrom(
        combineLatest([traceDone$, deliveries.completed$]).pipe(
          filter(([trace, delivery]) => trace && delivery),
          take(1),
          timeout(15000),
          takeUntil(stopped$)
        )
      ).then(() => undefined);
    },
    waitForPosition(sequence) {
      return firstValueFrom(
        admitted$.pipe(
          filter(position => position >= sequence),
          take(1),
          timeout(15000),
          takeUntil(stopped$)
        )
      );
    },
    dispose(error = new Error('Lifecycle replay disposed')) {
      abort(error);
      ownedSockets.forEach(socket => socket.close());
      ownedSockets.clear();
      sockets.clear();
    }
  };
}
