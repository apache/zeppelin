/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { expect, test } from '@playwright/test';

type ClientMessage = Record<string, unknown>;

test.describe('Classic WebSocket authentication boundary', () => {
  test('Given ticket metadata is loaded When a message is sent Then identity fields stay server-side', async ({
    page
  }) => {
    const sentMessages: ClientMessage[] = [];

    await test.step('Given the WebSocket transport is observed before navigation', async () => {
      await page.routeWebSocket('**/ws', webSocket => {
        webSocket.onMessage(message => {
          sentMessages.push(JSON.parse(message.toString()) as ClientMessage);
        });
      });
    });

    await test.step('When the classic UI loads its ticket metadata and opens WebSocket', async () => {
      const ticketResponse = page.waitForResponse(response => /\/api\/security\/ticket(?:\?|$)/.test(response.url()));
      await page.goto('/classic', { waitUntil: 'domcontentloaded' });
      await expect(page.locator('#welcome')).toHaveText('Welcome to Zeppelin!', { timeout: 30000 });
      expect((await ticketResponse).ok()).toBe(true);
      await expect.poll(() => sentMessages.length).toBeGreaterThan(0);
    });

    await test.step('Then outbound messages contain no client-asserted identity', async () => {
      for (const message of sentMessages) {
        expect(message).not.toHaveProperty('principal');
        expect(message).not.toHaveProperty('roles');
        expect(message).not.toHaveProperty('ticket');
      }
    });
  });

  test('Given a policy violation close When the session ends Then login is requested without reconnect', async ({
    page
  }) => {
    let connectionCount = 0;
    let policyCloseSent = false;

    await test.step('Given the WebSocket closes the first active connection with code 1008', async () => {
      await page.clock.install();
      await page.route('**/api/security/ticket', async route => {
        const response = await route.fetch();
        const ticket = (await response.json()) as { body: Record<string, string> };
        ticket.body.principal = 'test-user';
        ticket.body.ticket = 'test-session';
        await route.fulfill({ response, json: ticket });
      });
      await page.routeWebSocket('**/ws', webSocket => {
        connectionCount += 1;
        webSocket.onMessage(async () => {
          if (!policyCloseSent) {
            policyCloseSent = true;
            await webSocket.close({ code: 1008, reason: 'Session expired' });
          }
        });
      });
    });

    await test.step('When the classic UI establishes its WebSocket', async () => {
      await page.goto('/classic', { waitUntil: 'domcontentloaded' });
      await expect(page.locator('#welcome')).toHaveText('Welcome to Zeppelin!', { timeout: 30000 });
      await expect.poll(() => policyCloseSent).toBe(true);
    });

    await test.step('Then the login dialog explains the ended session without reconnecting', async () => {
      await page.clock.fastForward(2500);
      await expect(page.locator('#loginModal')).toBeVisible();
      await expect(page.locator('#loginModal .alert-danger')).toHaveText('Session expired');
      expect(connectionCount).toBe(1);
    });
  });
});
