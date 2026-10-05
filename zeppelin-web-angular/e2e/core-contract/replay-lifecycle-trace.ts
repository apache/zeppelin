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
import { expect, type Browser, type BrowserContext, type Page } from '@playwright/test';
import {
  createLifecycleReplay,
  type LifecycleFixture,
  type LifecycleFault,
  type LifecycleRecord,
  type LifecycleReplay,
  type RouteContext
} from './notebook-lifecycle-fixture.mjs';
import type { FixtureRestRequest } from './notebook-transport-fixture.mjs';

declare global {
  interface Window {
    traceSockets: Record<string, WebSocket>;
    traceFrames: string[];
  }
}

export interface LifecycleReplayConsumer {
  initialize(page: Page, sessionId: string): Promise<void>;
  enterContext(page: Page, context: RouteContext): Promise<void>;
  beforeSend(page: Page, record: Extract<LifecycleRecord, { kind: 'websocket' }>): Promise<void>;
  complete(viewers: ReadonlyMap<string, Page>): Promise<void>;
}

const openRecordedSocket = async (page: Page, connectionId: string) => {
  await page.evaluate(id => {
    const state = window;
    const socket = new WebSocket('ws://fixture.test/ws');
    state.traceSockets[id] = socket;
    socket.addEventListener('message', event => state.traceFrames.push(String(event.data)));
  }, connectionId);
  await expect.poll(() => page.evaluate(id => window.traceSockets[id].readyState, connectionId)).toBe(1);
};

const sendRecordedFrame = (page: Page, record: Extract<LifecycleRecord, { kind: 'websocket' }>) =>
  page.evaluate(({ id, payload }) => window.traceSockets[id].send(payload), {
    id: record.connectionId,
    payload: record.websocket.payloadText
  });

const requestOptions = (request: FixtureRestRequest): RequestInit => {
  const options: RequestInit = { method: request.method, headers: request.headers };
  if (request.method === 'GET' || request.method === 'HEAD') return options;
  if (request.bodyRaw === '' && request.bodyJson === undefined) return options;
  options.body = request.bodyRaw ?? JSON.stringify(request.bodyJson);
  return options;
};

const startRecordedRequest = (page: Page, request: FixtureRestRequest) =>
  page.evaluate(async ({ url, options }) => (await fetch(url, options)).text(), {
    url: request.url,
    options: requestOptions(request)
  });

const driveRecord = async (
  page: Page,
  record: LifecycleRecord,
  replay: LifecycleReplay,
  registerRequest: (request: Promise<unknown>) => void,
  consumer?: LifecycleReplayConsumer
) => {
  switch (record.kind) {
    case 'connection':
      if (record.event === 'open') await openRecordedSocket(page, record.connectionId);
      return;
    case 'context':
      await consumer?.enterContext(page, record.context);
      replay.context(record.sessionId, record.context);
      return;
    case 'websocket':
      if (record.websocket.direction === 'send') {
        await consumer?.beforeSend(page, record);
        await sendRecordedFrame(page, record);
      }
      return;
    case 'rest':
      if (record.rest.direction === 'request') registerRequest(startRecordedRequest(page, record.rest.request));
  }
};

const observeFrames = async (
  viewers: ReadonlyMap<string, Page>,
  fixture: LifecycleFixture,
  faults: LifecycleFault[]
) => {
  const observed = new Map<string, Record<string, unknown>[]>();
  const copies = new Map(faults.map(fault => [fault.sequence, fault.copies ?? 1]));
  for (const [sessionId, page] of viewers) {
    const expectedCount = fixture.records
      .filter(
        record =>
          record.sessionId === sessionId && record.kind === 'websocket' && record.websocket.direction === 'receive'
      )
      .reduce((count, record) => count + (copies.get(record.sequence) ?? 1), 0);
    await expect.poll(() => page.evaluate(() => window.traceFrames.length)).toBe(expectedCount);
    observed.set(
      sessionId,
      await page.evaluate(() => window.traceFrames.map(frame => JSON.parse(frame) as Record<string, unknown>))
    );
  }
  return observed;
};

/** Drives captured transport in independent browsers; an optional consumer reads the same native deliveries. */
export const replayLifecycleTrace = async (
  browser: Browser,
  fixture: LifecycleFixture,
  faults: LifecycleFault[] = [],
  consumer?: LifecycleReplayConsumer
) => {
  const replay = createLifecycleReplay(fixture, faults);
  const contexts: BrowserContext[] = [];
  const viewers = new Map<string, Page>();
  const requests: Promise<unknown>[] = [];
  let failure: unknown;
  const registerRequest = (request: Promise<unknown>) => {
    requests.push(request);
    void request.catch(error => {
      failure ??= error;
    });
  };
  const position = () => {
    if (failure !== undefined) throw failure;
    return replay.position();
  };
  try {
    for (const session of fixture.sessions) {
      const context = await browser.newContext();
      contexts.push(context);
      const page = await context.newPage();
      viewers.set(session.id, page);
      await page.route('http://fixture.test/', route =>
        route.fulfill({
          contentType: 'text/html',
          body: '<html><body></body></html>'
        })
      );
      await replay.install(page, session.id);
      await page.goto('http://fixture.test/');
      await page.evaluate(() => Object.assign(window, { traceSockets: {}, traceFrames: [] }));
      await consumer?.initialize(page, session.id);
    }

    for (const record of fixture.records) {
      const index = record.sequence - 1;
      if (position() > index) continue;
      await expect.poll(position, { timeout: 15000 }).toBeGreaterThanOrEqual(index);
      if (position() === index)
        await driveRecord(viewers.get(record.sessionId)!, record, replay, registerRequest, consumer);
    }

    await Promise.all(requests);
    await expect.poll(() => replay.isComplete(), { timeout: 15000 }).toBe(true);
    const observed = await observeFrames(viewers, fixture, faults);
    replay.assertComplete();
    await consumer?.complete(viewers);
    return observed;
  } catch (error) {
    failure = error;
    throw error;
  } finally {
    replay.dispose();
    const cleanup = await Promise.allSettled(contexts.map(context => context.close()));
    const errors = cleanup.flatMap(result => (result.status === 'rejected' ? [result.reason] : []));
    if (errors.length) {
      throw new AggregateError(failure === undefined ? errors : [failure, ...errors], 'Replay browser cleanup failed');
    }
  }
};
