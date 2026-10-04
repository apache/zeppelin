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
import { expect, type Browser } from '@playwright/test';
import { createLifecycleReplay, type LifecycleFixture, type LifecycleFault } from './notebook-lifecycle-fixture.mjs';

/** Replays wire traffic in independent real browsers; this is not a Shared Core implementation. */
export const replayLifecycleTrace = async (
  browser: Browser,
  fixture: LifecycleFixture,
  faults: LifecycleFault[] = []
) => {
  const replay = createLifecycleReplay(fixture, faults);
  const viewers = new Map<string, Awaited<ReturnType<typeof browser.newPage>>>();
  const requests: Promise<unknown>[] = [];
  try {
    for (const session of fixture.sessions) {
      const context = await browser.newContext();
      const page = await context.newPage();
      viewers.set(session.id, page);
      await page.route('http://fixture.test/', route =>
        route.fulfill({
          contentType: 'text/html',
          body: '<html><body><pre role="status" aria-label="Observed frames"></pre></body></html>'
        })
      );
      await replay.install(page, session.id);
      await page.goto('http://fixture.test/');
      await page.evaluate(() => {
        Object.assign(window, { traceSockets: {}, traceFrames: [] });
      });
    }
    for (const record of fixture.records) {
      const index = record.sequence - 1;
      if (replay.position() > index) continue;
      await expect.poll(() => replay.position(), { timeout: 15000 }).toBeGreaterThanOrEqual(index);
      if (replay.position() > index) continue;
      const page = viewers.get(record.sessionId)!;
      if (record.kind === 'connection' && record.event === 'open') {
        await page.evaluate(id => {
          const state = window as unknown as { traceSockets: Record<string, WebSocket>; traceFrames: string[] };
          const socket = new WebSocket('ws://fixture.test/ws');
          state.traceSockets[id] = socket;
          socket.addEventListener('message', event => {
            state.traceFrames.push(String(event.data));
            document.querySelector('pre')!.textContent = state.traceFrames.join('\n');
          });
        }, record.connectionId);
        await expect
          .poll(() =>
            page.evaluate(id => {
              const state = window as unknown as { traceSockets: Record<string, WebSocket> };
              return state.traceSockets[id].readyState;
            }, record.connectionId)
          )
          .toBe(1);
      } else if (record.kind === 'context') {
        replay.context(record.sessionId, record.context);
      } else if (record.kind === 'websocket' && record.websocket.direction === 'send') {
        await page.evaluate(
          ({ id, payload }) => {
            const state = window as unknown as { traceSockets: Record<string, WebSocket> };
            state.traceSockets[id].send(payload);
          },
          { id: record.connectionId, payload: record.websocket.payloadText! }
        );
      } else if (record.kind === 'rest' && record.rest.direction === 'request') {
        requests.push(
          page.evaluate(async request => {
            const response = await fetch(request.url, {
              method: request.method,
              headers: request.headers,
              ...(request.method === 'GET' || request.method === 'HEAD'
                ? {}
                : {
                    ...(request.bodyRaw === '' && request.bodyJson === undefined
                      ? {}
                      : {
                          body: request.bodyRaw ?? JSON.stringify(request.bodyJson)
                        })
                  })
            });
            return response.text();
          }, record.rest.request)
        );
      }
    }
    await Promise.all(requests);
    await expect
      .poll(
        () => {
          try {
            replay.assertComplete();
            return true;
          } catch {
            return false;
          }
        },
        { timeout: 15000 }
      )
      .toBe(true);
    const observed = new Map<string, Record<string, unknown>[]>();
    for (const [sessionId, page] of viewers) {
      const expectedCount = fixture.records
        .filter(
          record =>
            record.sessionId === sessionId && record.kind === 'websocket' && record.websocket.direction === 'receive'
        )
        .reduce((count, record) => count + (faults.find(fault => fault.sequence === record.sequence)?.copies ?? 1), 0);
      await expect
        .poll(() => page.evaluate(() => (window as unknown as { traceFrames: string[] }).traceFrames.length))
        .toBe(expectedCount);
      const frames = await page.evaluate(() =>
        (window as unknown as { traceFrames: string[] }).traceFrames.map(
          frame => JSON.parse(frame) as Record<string, unknown>
        )
      );
      observed.set(sessionId, frames);
      await expect(page.getByRole('status', { name: 'Observed frames', exact: true })).toHaveText(
        frames.map(frame => JSON.stringify(frame)).join('\n')
      );
    }
    replay.assertComplete();
    return observed;
  } finally {
    replay.dispose();
    await Promise.all([...viewers.values()].map(page => page.context().close()));
  }
};
