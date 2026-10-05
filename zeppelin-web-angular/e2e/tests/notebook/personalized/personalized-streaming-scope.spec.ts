/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { BrowserContext, expect, Page, test } from '@playwright/test';
import { CollaborationPage } from 'e2e/models/collaboration-page';
import { NotebookParagraphPage } from 'e2e/models/notebook-paragraph-page';
import { addPageAnnotationBeforeEach, createTestNotebook, getTwoTestAccounts, loginAs, PAGES } from '../../../utils';

// browser.newContext() inherits the project's storageState, already signed in as the first
// account. These tests choose their own principal, so they start from nothing.
const SIGNED_OUT = { cookies: [], origins: [] };

/**
 * Records the incremental-output frames the server sends to one page. The defect is in which
 * connection the server addresses, so the frames are the subject rather than what the UI draws.
 * Attach before the page navigates: the socket opens as the app boots.
 */
const recordStreamingFrames = (page: Page): string[] => {
  const frames: string[] = [];
  page.on('websocket', socket => {
    socket.on('framereceived', frame => {
      const payload = typeof frame.payload === 'string' ? frame.payload : frame.payload.toString();
      if (payload.includes('PARAGRAPH_APPEND_OUTPUT') || payload.includes('PARAGRAPH_UPDATE_OUTPUT')) {
        frames.push(payload);
      }
    });
  });
  return frames;
};

/** The pid the shell printed, which differs for every execution of the same code. */
const markerIn = (frames: string[]): string => {
  const marker = /MARK-\d+/.exec(frames.join('\n'));
  expect(marker, `no marker in ${frames.length} streaming frames`).not.toBeNull();
  return marker![0];
};

test.describe('Personalized streaming scope', () => {
  // JUSTIFIED: one shared notebook and two signed-in principals must stay within one worker.
  test.describe.configure({ mode: 'default' });
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

  let ownerContext: BrowserContext;
  let otherContext: BrowserContext;

  test.afterEach(async () => {
    await ownerContext?.close();
    await otherContext?.close();
  });

  test('sends each user only the streaming output of their own run', async ({ browser }) => {
    const accounts = await getTwoTestAccounts();
    test.skip(!accounts, 'Two shiro accounts are required to tell personalized users apart');
    const [ownerAccount, otherAccount] = accounts!;

    ownerContext = await browser.newContext({ storageState: SIGNED_OUT });
    otherContext = await browser.newContext({ storageState: SIGNED_OUT });
    const ownerPage = await ownerContext.newPage();
    const otherPage = await otherContext.newPage();
    const ownerFrames = recordStreamingFrames(ownerPage);
    const otherFrames = recordStreamingFrames(otherPage);

    const ownerNote = new CollaborationPage(ownerPage);
    const otherNote = new CollaborationPage(otherPage);
    const ownerParagraph = new NotebookParagraphPage(ownerPage);
    const otherParagraph = new NotebookParagraphPage(otherPage);

    await test.step('Given a personalized note two signed-in users share', async () => {
      await loginAs(ownerPage, ownerAccount);
      await loginAs(otherPage, otherAccount);

      const { noteId } = await createTestNotebook(ownerPage);
      await ownerNote.openNotebook(noteId);
      await ownerNote.switchToPersonalModeButton.click();
      await ownerNote.confirmPersonalizedModeChange();
      await expect(ownerNote.switchToCollaborationModeButton).toBeVisible({ timeout: 15000 });

      await otherNote.openNotebook(noteId);
    });

    await test.step('When both run that paragraph', async () => {
      // Personalized mode shares and live-syncs the paragraph text, so the two users cannot hold
      // different code. The shell pid differs per run, giving each execution its own marker.
      await ownerNote.typeInEditor('%sh\necho MARK-$$');
      await expect(otherNote.editorText).toContainText('MARK-', { timeout: 15000 });

      await ownerParagraph.runParagraph();
      await otherParagraph.runParagraph();

      await expect(ownerParagraph.status).toHaveText('FINISHED');
      await expect(otherParagraph.status).toHaveText('FINISHED');
    });

    await test.step('Then neither socket carried the other run output', async () => {
      const ownerMarker = markerIn(ownerFrames);
      const otherMarker = markerIn(otherFrames);
      expect(ownerMarker).not.toEqual(otherMarker);

      expect(ownerFrames.join('\n')).not.toContain(otherMarker);
      expect(otherFrames.join('\n')).not.toContain(ownerMarker);
    });

    await test.step('And the terminal result each user sees matches their own run', async () => {
      const ownerMarker = markerIn(ownerFrames);
      const otherMarker = markerIn(otherFrames);

      await expect(ownerParagraph.resultDisplay).toContainText(ownerMarker);
      await expect(ownerParagraph.resultDisplay).not.toContainText(otherMarker);
      await expect(otherParagraph.resultDisplay).toContainText(otherMarker);
      await expect(otherParagraph.resultDisplay).not.toContainText(ownerMarker);
    });
  });

  test('sends no streaming output to a user who only opened the note', async ({ browser }) => {
    const accounts = await getTwoTestAccounts();
    test.skip(!accounts, 'Two shiro accounts are required to tell personalized users apart');
    const [ownerAccount, otherAccount] = accounts!;

    ownerContext = await browser.newContext({ storageState: SIGNED_OUT });
    otherContext = await browser.newContext({ storageState: SIGNED_OUT });
    const ownerPage = await ownerContext.newPage();
    const watcherPage = await otherContext.newPage();
    const ownerFrames = recordStreamingFrames(ownerPage);
    const watcherFrames = recordStreamingFrames(watcherPage);

    await loginAs(ownerPage, ownerAccount);
    await loginAs(watcherPage, otherAccount);

    const { noteId } = await createTestNotebook(ownerPage);
    const ownerNote = new CollaborationPage(ownerPage);
    await ownerNote.openNotebook(noteId);
    await ownerNote.switchToPersonalModeButton.click();
    await ownerNote.confirmPersonalizedModeChange();
    await expect(ownerNote.switchToCollaborationModeButton).toBeVisible({ timeout: 15000 });
    await new CollaborationPage(watcherPage).openNotebook(noteId);

    await ownerNote.typeInEditor('%sh\necho RUNNER-ONLY');
    const ownerParagraph = new NotebookParagraphPage(ownerPage);
    await ownerParagraph.runParagraph();
    await expect(ownerParagraph.status).toHaveText('FINISHED');
    await expect(ownerParagraph.resultDisplay).toContainText('RUNNER-ONLY');

    // Both halves matter. On their own "the watcher got nothing" also holds when the server
    // sends the output to nobody, so it would pass against a build that simply drops it.
    expect(ownerFrames.join('\n')).toContain('RUNNER-ONLY');
    expect(watcherFrames).toHaveLength(0);
    await expect(new NotebookParagraphPage(watcherPage).resultDisplay).toHaveCount(0);
  });
});
