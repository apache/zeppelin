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
import { randomUUID } from 'node:crypto';
import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { E2E_TEST_FOLDER } from '../../../models/base-page';
import { NotebookOfflineRecoveryPage, type ConnectivitySnapshot } from '../../../models/notebook-offline-recovery-page';
import { addPageAnnotationBeforeEach, PAGES, waitForZeppelinReady } from '../../../utils';

const paragraphText = async (page: Page, noteId: string): Promise<string> => {
  const response = await page.request.get(`/api/notebook/${noteId}`);
  if (!response.ok()) throw new Error(`Canonical notebook fetch failed: ${response.status()}`);
  return (await response.json()).body.paragraphs[0].text as string;
};

const attemptsInFirstSecond = (snapshot: ConnectivitySnapshot, transition: 'offlineAt' | 'onlineAt'): number => {
  const started = snapshot[transition];
  if (started === null) throw new Error(`No native ${transition} event was observed`);
  return snapshot.attempts.filter(attempt => attempt >= started && attempt < started + 1000).length;
};

test.describe('Notebook offline recovery', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);
  let view: NotebookOfflineRecoveryPage;

  test.beforeEach(async ({ page }) => {
    view = new NotebookOfflineRecoveryPage(page);
    await view.install();
  });

  test('pauses offline retries and recovers missed and live peer edits without reloading', async ({
    page,
    context,
    browser,
    baseURL
  }, testInfo) => {
    const marker = randomUUID();
    const initialText = `%md initial_${marker}`;
    const missedText = `%md missed_${marker}`;
    const liveText = `%md live_${marker}`;
    const created = await page.request.post('/api/notebook', {
      data: {
        notePath: `${E2E_TEST_FOLDER}/OfflineRecovery_${marker}`,
        paragraphs: [{ text: initialText }]
      }
    });
    expect(created.ok()).toBe(true);
    const noteId = (await created.json()).body as string;
    let peerContext: BrowserContext | undefined;
    const observations: Record<string, ConnectivitySnapshot> = {};

    try {
      // Both viewers inherit the suite's principal; this also works with anonymous storageState.
      peerContext = await browser.newContext({ baseURL, storageState: await context.storageState() });
      const peerPage = await peerContext.newPage();
      const peer = new NotebookOfflineRecoveryPage(peerPage);
      await peer.install();

      await test.step('Given two real notebook viewers share an isolated note', async () => {
        await Promise.all([page.goto(`/#/notebook/${noteId}`), peerPage.goto(`/#/notebook/${noteId}`)]);
        await Promise.all([waitForZeppelinReady(page), waitForZeppelinReady(peerPage)]);
        await expect(view.editorText).toContainText(initialText);
        await expect(peer.editorText).toContainText(initialText);
        observations.initial = await view.snapshot();
        expect(observations.initial).toMatchObject({
          online: true,
          open: 1,
          active: 1,
          maxOpen: 1,
          maxActive: 1,
          socketStates: [1]
        });
        expect(observations.initial.attempts).toHaveLength(1);
        await expect.poll(async () => (await peer.snapshot()).open).toBe(1);
        expect((await peer.snapshot()).documentId).not.toBe(observations.initial.documentId);
      });

      await test.step('When the browser context goes offline, the socket closes without a retry storm', async () => {
        await context.setOffline(true);
        await expect.poll(async () => (await view.snapshot()).online).toBe(false);
        await expect.poll(async () => (await view.snapshot()).offlineAt).not.toBeNull();
        await expect(view.reconnecting).toBeVisible();
        observations.firstSecond = await view.observe('offlineAt', 1000);
        expect(attemptsInFirstSecond(observations.firstSecond, 'offlineAt')).toBeLessThanOrEqual(1);
        expect(observations.firstSecond.documentId).toBe(observations.initial.documentId);
        await expect.poll(async () => (await view.snapshot()).active, { timeout: 15000 }).toBe(0);
      });

      await test.step('And the peer persists a change while the first viewer remains paused offline', async () => {
        await peer.replaceText(missedText);
        await expect(peer.editorText).toContainText(missedText);
        await expect.poll(() => paragraphText(peerPage, noteId), { timeout: 15000 }).toBe(missedText);
        observations.paused = await view.observe('offlineAt', 3500);
        expect(observations.paused).toMatchObject({ online: false, open: 0, active: 0, maxOpen: 1, maxActive: 1 });
        expect(observations.paused.attempts).toHaveLength(observations.firstSecond.attempts.length);
        expect(observations.paused.documentId).toBe(observations.initial.documentId);
        expect(observations.paused.socketStates.every(state => state === 3)).toBe(true);
        await expect(view.editorText).toContainText(initialText);
        await expect(view.editorText).not.toContainText(missedText);
      });

      await test.step('Then going online rehydrates the missed change in the same document with one socket', async () => {
        await context.setOffline(false);
        await expect.poll(async () => (await view.snapshot()).onlineAt).not.toBeNull();
        await expect(view.editorText).toContainText(missedText, { timeout: 30000 });
        await expect(view.reconnecting).toBeHidden();
        observations.recovered = await view.observe('onlineAt', 1000);
        expect(attemptsInFirstSecond(observations.recovered, 'onlineAt')).toBeLessThanOrEqual(1);
        expect(observations.recovered.attempts).toHaveLength(observations.paused.attempts.length + 1);
        expect(observations.recovered).toMatchObject({ online: true, open: 1, active: 1, maxOpen: 1, maxActive: 1 });
        expect(observations.recovered.documentId).toBe(observations.initial.documentId);
        expect(observations.recovered.socketStates).toEqual([...observations.paused.socketStates, 1]);
        await expect(page).toHaveURL(new RegExp(`/#/notebook/${noteId}$`));
      });

      await test.step('And a subsequent peer edit arrives live without another reconnect or reload', async () => {
        await peer.replaceText(liveText);
        await expect(peer.editorText).toContainText(liveText);
        await expect(view.editorText).toContainText(liveText, { timeout: 30000 });
        observations.live = await view.snapshot();
        expect(observations.live.documentId).toBe(observations.initial.documentId);
        expect(observations.live.attempts).toHaveLength(observations.recovered.attempts.length);
        expect(observations.live).toMatchObject({ online: true, open: 1, active: 1, maxOpen: 1, maxActive: 1 });
        expect(await peer.snapshot()).toMatchObject({ online: true, open: 1, active: 1, maxOpen: 1, maxActive: 1 });
      });
    } finally {
      try {
        await testInfo.attach('native-websocket-lifecycle', {
          body: JSON.stringify(observations, null, 2),
          contentType: 'application/json'
        });
      } finally {
        try {
          const removed = await page.request.delete(`/api/notebook/${noteId}`);
          expect(removed.ok()).toBe(true);
        } finally {
          await Promise.all([context.setOffline(false), peerContext?.close()]);
        }
      }
    }
  });
});
