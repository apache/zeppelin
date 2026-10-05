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
const requestKey = record => `${record.sessionId}:${record.requestId}`;
const restRequest = record => record.kind === 'rest' && record.rest.direction === 'request';
const connectionOpen = record => record.kind === 'connection' && record.event === 'open';

class LifecycleReplay {
  #fixture;
  #inputs;
  #correlation;
  #deliveries;
  #sockets = new Map();
  #ownedSockets = new Set();
  #requests = new Map();
  #installedSessions = new Set();
  #admitted$ = new BehaviorSubject(0);
  #stopped$ = new Subject();
  #traceDone$ = new BehaviorSubject(false);
  #fatalError;

  constructor(fixture, faults) {
    const errors = validateLifecycleFixture(fixture);
    if (errors.length) throw new Error(errors.join('\n'));
    this.#fixture = fixture;
    this.#correlation = createMessageCorrelation(fixture);
    // Each expected input has one bounded gate; unmatched traffic never enters the stream.
    this.#inputs = new Map(
      fixture.records.filter(requiresRuntimeInput).map(record => [record.sequence, new ReplaySubject(1)])
    );
    this.#deliveries = createLifecycleDeliveryScheduler(
      fixture,
      faults,
      this.#admitted$,
      (record, copies) => this.#deliver(record, copies),
      error => this.#abort(error)
    );
    this.#startTrace();
  }

  #startTrace() {
    from(this.#fixture.records)
      .pipe(
        concatMap(record => {
          const gate = this.#inputs.get(record.sequence);
          return gate
            ? gate.pipe(
                take(1),
                concatMap(input => this.#execute(record, input))
              )
            : defer(() => this.#execute(record));
        }),
        takeUntil(this.#stopped$)
      )
      .subscribe({ error: error => this.#abort(error), complete: () => this.#traceDone$.next(true) });
  }

  #abort(error) {
    if (this.#fatalError) return;
    this.#fatalError = error;
    this.#stopped$.error(error);
    this.#admitted$.error(error);
    this.#deliveries.dispose();
    this.#inputs.clear();
    this.#requests.clear();
  }

  #fail(error) {
    this.#abort(error);
    throw this.#fatalError;
  }

  #advance(record) {
    // Valid context/send inputs may arrive during REST fulfillment. Outputs remain serialized.
    this.#admitted$.next(Math.max(this.#admitted$.value, record.sequence));
  }

  #publish(record, value) {
    this.position();
    const gate = this.#inputs.get(record.sequence);
    if (gate.isStopped) {
      return this.#fail(new Error('Lifecycle input was already consumed'));
    }
    gate.next(value);
    gate.complete();

    if (!this.#fatalError && (record.kind === 'context' || record.kind === 'websocket')) {
      this.#advance(record);
    }
  }

  #bindMessageId(binding) {
    if (!binding) return;
    const [recorded, runtime] = binding;
    this.#correlation.recorded.set(recorded, runtime);
    this.#correlation.runtime.set(runtime, recorded);
  }

  #deliver(record, copies) {
    if (!copies) return;
    const socket = this.#sockets.get(record.connectionId);
    if (!socket) {
      throw new Error('Delayed frame targets a closed connection');
    }

    const { binding, payload } = incomingFrame(record, this.#correlation);
    this.#bindMessageId(binding);
    for (let copy = 0; copy < copies; copy++) {
      socket.send(payload);
    }
  }

  #disconnect(record) {
    const socket = this.#sockets.get(record.connectionId);
    socket.close({ code: 1012, reason: 'Recorded disconnect' });
    this.#ownedSockets.delete(socket);
    this.#sockets.delete(record.connectionId);
  }

  #fulfillResponse(record) {
    const key = requestKey(record);
    const request = this.#requests.get(key);
    // Admit the next input before fulfillment, without executing later outputs ahead of it.
    this.#advance(record);
    return defer(() =>
      request.route.fulfill({
        status: record.rest.status,
        headers: record.rest.headers,
        body: record.rest.bodyRaw ?? JSON.stringify(record.rest.bodyJson)
      })
    ).pipe(
      tap(() => {
        this.#requests.delete(key);
        request.completed.next();
        request.completed.complete();
      })
    );
  }

  #execute(record, input) {
    this.#inputs.delete(record.sequence);
    if (record.kind === 'connection') {
      if (record.event === 'open') {
        this.#sockets.set(record.connectionId, input);
      } else {
        this.#disconnect(record);
      }
    }
    if (record.kind === 'rest' && record.rest.direction === 'response') {
      return this.#fulfillResponse(record);
    }
    this.#advance(record);
    return EMPTY;
  }

  #pendingInput(sessionId, predicate, matches = () => true) {
    const batch = consecutiveRecords(this.#fixture.records, this.position(), predicate);
    return batch.find(
      record => record.sessionId === sessionId && !this.#inputs.get(record.sequence).isStopped && matches(record)
    );
  }

  async #acceptRest(sessionId, route, request) {
    if (!isNotebookRestUrl(request.url())) return route.fallback();

    const shape = captureRestRequest(request);
    const record = this.#pendingInput(sessionId, restRequest, record => matchesRequest(record.rest.request, shape));
    if (!record) {
      return this.#fail(new Error(`Unexpected lifecycle REST request: ${stableJson(shape)}`));
    }

    const completed = new AsyncSubject();
    this.#requests.set(requestKey(record), { route, completed });

    const response = firstValueFrom(completed.pipe(takeUntil(this.#stopped$)));
    this.#publish(record, request);

    await response;
  }

  #acceptSocket(sessionId, socket) {
    const record = this.#pendingInput(sessionId, connectionOpen);
    if (!record) {
      return this.#fail(new Error('Unexpected lifecycle WebSocket connection'));
    }

    // Own the socket on admission, even while its execution gate is waiting for another session.
    this.#ownedSockets.add(socket);
    socket.onMessage(payload => this.#acceptSend(sessionId, socket, payload));
    this.#publish(record, socket);
  }

  #acceptSend(sessionId, socket, payload) {
    const expected = this.#fixture.records[this.position()];
    if (expected?.kind !== 'websocket') {
      return this.#fail(new Error('Lifecycle WebSocket send out of order: expected another record kind'));
    }
    if (expected.websocket.direction !== 'send') {
      return this.#fail(new Error('Lifecycle WebSocket send out of order: expected a receive'));
    }
    if (expected.sessionId !== sessionId || this.#sockets.get(expected.connectionId) !== socket) {
      return this.#fail(new Error('Lifecycle WebSocket send out of order'));
    }

    try {
      this.#bindMessageId(matchOutgoingFrame(expected, payload, this.#correlation));
    } catch (error) {
      return this.#fail(error);
    }

    this.#publish(expected, payload);
  }

  async install(page, sessionId) {
    this.position();

    if (this.#installedSessions.has(sessionId)) {
      throw new Error('Replay session is already installed');
    }
    if (!this.#fixture.sessions.some(session => session.id === sessionId)) {
      throw new Error('Replay requires a recorded session');
    }

    this.#installedSessions.add(sessionId);

    await page.route('**/api/**', (route, request) => this.#acceptRest(sessionId, route, request));
    await page.routeWebSocket(notebookSocket, socket => this.#acceptSocket(sessionId, socket));
  }

  context(sessionId, context) {
    const record = this.#fixture.records[this.position()];
    if (record?.kind !== 'context') {
      return this.#fail(new Error('Lifecycle route context out of order: expected another record kind'));
    }
    if (record.sessionId !== sessionId) {
      return this.#fail(new Error('Lifecycle route context out of order: wrong session'));
    }
    if (stableJson(record.context) !== stableJson(context)) {
      return this.#fail(new Error('Lifecycle route context out of order: different route'));
    }

    this.#publish(record, context);
  }

  position() {
    if (this.#fatalError) throw this.#fatalError;
    return this.#admitted$.value;
  }

  isComplete() {
    this.position();
    return this.#traceDone$.value && !this.#deliveries.hasPending();
  }

  assertComplete() {
    if (this.isComplete()) return;
    const pending = {
      records: this.#fixture.records.length - this.position(),
      restRequests: this.#requests.size,
      scheduledDelivery: this.#deliveries.hasPending()
    };
    throw new Error(`Lifecycle fixture has unconsumed records or pending deliveries: ${JSON.stringify(pending)}`);
  }

  waitForComplete() {
    return firstValueFrom(
      combineLatest([this.#traceDone$, this.#deliveries.completed$]).pipe(
        filter(([trace, delivery]) => trace && delivery),
        take(1),
        timeout(15000),
        takeUntil(this.#stopped$)
      )
    ).then(() => undefined);
  }

  waitForPosition(sequence) {
    return firstValueFrom(
      this.#admitted$.pipe(
        filter(position => position >= sequence),
        take(1),
        timeout(15000),
        takeUntil(this.#stopped$)
      )
    );
  }

  dispose(error = new Error('Lifecycle replay disposed')) {
    this.#abort(error);
    const sockets = [...this.#ownedSockets];
    this.#ownedSockets.clear();
    this.#sockets.clear();

    const errors = [];
    for (const socket of sockets) {
      try {
        socket.close();
      } catch (error) {
        errors.push(error);
      }
    }

    if (errors.length) {
      throw new AggregateError(errors, 'Replay socket cleanup failed');
    }
  }
}

export function createLifecycleReplay(fixture, faults = []) {
  return new LifecycleReplay(fixture, faults);
}
