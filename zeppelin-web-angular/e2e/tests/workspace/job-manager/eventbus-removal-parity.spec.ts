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

import { Browser, expect, Page, test } from '@playwright/test';
import { JobManagerPage } from 'e2e/models/job-manager-page';
import { LoginPage } from 'e2e/models/login-page';
import { LoginTestUtil, TestCredentials } from 'e2e/models/login-page.util';
import { addPageAnnotationBeforeEach, PAGES } from '../../../utils';

interface NoteJob {
  noteId?: string;
  isRunningJob?: boolean;
  isRemoved?: boolean;
  unixTimeLastRun?: number;
}

interface JobManagerMessage {
  op?: string;
  data?: {
    noteJobs?: { jobs?: NoteJob[] };
    noteRunningJobs?: { jobs?: NoteJob[] };
  };
}

class JobManagerMessageRecorder {
  private readonly messages: JobManagerMessage[] = [];

  constructor(page: Page) {
    page.on('websocket', socket => {
      if (new URL(socket.url()).pathname !== '/ws') {
        return;
      }
      socket.on('framereceived', frame => {
        const payload = frame.payload.toString();
        if (payload.startsWith('{')) {
          this.messages.push(JSON.parse(payload) as JobManagerMessage);
        }
      });
    });
  }

  hasInitialList(): boolean {
    return this.messages.some(message => message.op === 'LIST_NOTE_JOBS');
  }

  initialNoteIds(): string[] {
    const initial = [...this.messages].reverse().find(message => message.op === 'LIST_NOTE_JOBS');
    return initial?.data?.noteJobs?.jobs?.flatMap(job => (job.noteId ? [job.noteId] : [])) ?? [];
  }

  removals(): NoteJob[] {
    return this.messages
      .filter(message => message.op === 'LIST_UPDATE_NOTE_JOBS')
      .flatMap(message => message.data?.noteRunningJobs?.jobs ?? [])
      .filter(job => job.isRemoved === true);
  }
}

const createNote = async (page: Page, label: string): Promise<{ noteId: string; noteName: string }> => {
  const noteName = `EventBusParity_${label}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const response = await page.request.post('/api/notebook', {
    data: {
      notePath: `E2E_TEST_FOLDER/${noteName}`,
      defaultInterpreterGroup: 'python',
      addingEmptyParagraph: true
    },
    failOnStatusCode: false
  });
  await expect(response).toBeOK();

  const body = (await response.json()) as { body?: string };
  expect(body.body).toEqual(expect.any(String));
  return { noteId: body.body as string, noteName };
};

const deleteNote = async (page: Page, noteId: string): Promise<void> => {
  const response = await page.request.delete(`/api/notebook/${noteId}`, { failOnStatusCode: false });
  await expect(response).toBeOK();
};

const expectNoteMissing = async (page: Page, noteId: string): Promise<void> => {
  await expect
    .poll(async () => (await page.request.get(`/api/notebook/${noteId}`, { failOnStatusCode: false })).status())
    .toBe(404);
};

const login = async (page: Page, credentials: TestCredentials): Promise<void> => {
  const loginPage = new LoginPage(page);
  await loginPage.navigate();
  await expect(loginPage.formContainer).toBeVisible();
  await loginPage.login(credentials.username, credentials.password);
  await expect(loginPage.formContainer).toBeHidden();
};

const expectServerEventBusMode = async (page: Page): Promise<void> => {
  const response = await page.request.get('/api/configurations/prefix/zeppelin.eventbus.enabled', {
    failOnStatusCode: false
  });
  await expect(response).toBeOK();

  const body = (await response.json()) as { body?: Record<string, string> };
  expect(body.body?.['zeppelin.eventbus.enabled']).toBe(
    process.env.ZEPPELIN_EVENTBUS_ENABLED === 'true' ? 'true' : 'false'
  );
};

const verifyRemovalParity = async (
  browser: Browser,
  ownerPage: Page,
  observerCredentials: TestCredentials | undefined,
  observerOwnsNotes: boolean
): Promise<{ ownerRemovalCount: number; observerRemovalCount: number }> => {
  await expectServerEventBusMode(ownerPage);
  const targetNote = await createNote(ownerPage, 'target');
  const barrierNote = await createNote(ownerPage, 'barrier');
  const observerContext = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const observerPage = await observerContext.newPage();

  try {
    const ownerRecorder = new JobManagerMessageRecorder(ownerPage);
    const observerRecorder = new JobManagerMessageRecorder(observerPage);

    if (observerCredentials) {
      await login(observerPage, observerCredentials);
    }

    const ownerJobManager = new JobManagerPage(ownerPage);
    const observerJobManager = new JobManagerPage(observerPage);

    await Promise.all([ownerJobManager.navigate(), observerJobManager.navigate()]);
    await expect.poll(() => ownerRecorder.hasInitialList()).toBe(true);
    await expect.poll(() => observerRecorder.hasInitialList()).toBe(true);

    expect(ownerRecorder.initialNoteIds()).toEqual(expect.arrayContaining([targetNote.noteId, barrierNote.noteId]));
    expect(observerRecorder.initialNoteIds().includes(targetNote.noteId)).toBe(observerOwnsNotes);
    expect(observerRecorder.initialNoteIds().includes(barrierNote.noteId)).toBe(observerOwnsNotes);
    await expect(ownerJobManager.jobItemByName(targetNote.noteName)).toBeVisible();
    await expect(observerJobManager.jobItemByName(targetNote.noteName)).toHaveCount(observerOwnsNotes ? 1 : 0);

    await deleteNote(ownerPage, targetNote.noteId);
    await expectNoteMissing(ownerPage, targetNote.noteId);
    await deleteNote(ownerPage, barrierNote.noteId);
    await expectNoteMissing(ownerPage, barrierNote.noteId);

    const expectedRemovalOrder = [targetNote.noteId, barrierNote.noteId];
    await expect.poll(() => ownerRecorder.removals().map(job => job.noteId)).toEqual(expectedRemovalOrder);
    await expect.poll(() => observerRecorder.removals().map(job => job.noteId)).toEqual(expectedRemovalOrder);
    await expect(ownerJobManager.jobItemByName(targetNote.noteName)).toHaveCount(0);

    for (const recorder of [ownerRecorder, observerRecorder]) {
      expect(recorder.removals()).toEqual([
        { noteId: targetNote.noteId, isRunningJob: false, isRemoved: true, unixTimeLastRun: 0 },
        { noteId: barrierNote.noteId, isRunningJob: false, isRemoved: true, unixTimeLastRun: 0 }
      ]);
    }

    return {
      ownerRemovalCount: ownerRecorder.removals().length,
      observerRemovalCount: observerRecorder.removals().length
    };
  } finally {
    await observerContext.close();
    await ownerPage.request.delete(`/api/notebook/${targetNote.noteId}`, { failOnStatusCode: false });
    await ownerPage.request.delete(`/api/notebook/${barrierNote.noteId}`, { failOnStatusCode: false });
  }
};

test.describe('Job Manager note-removal EventBus parity', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.JOB_MANAGER);

  test.beforeEach(async ({}, testInfo) => {
    testInfo.annotations.push({
      type: 'eventbus-mode',
      description: process.env.ZEPPELIN_EVENTBUS_ENABLED === 'true' ? 'eventbus' : 'legacy'
    });
  });

  test('anonymous viewers receive one ordered removal payload per deleted note', async ({ browser, page }) => {
    test.skip(await LoginTestUtil.isShiroEnabled(), 'ZEPPELIN-6698 requires the anonymous server matrix');
    const removalCounts = await verifyRemovalParity(browser, page, undefined, true);
    expect(removalCounts).toEqual({ ownerRemovalCount: 2, observerRemovalCount: 2 });
  });

  test('an authenticated non-owner receives one ordered removal payload per deleted note', async ({
    browser,
    page
  }) => {
    test.skip(!(await LoginTestUtil.isShiroEnabled()), 'ZEPPELIN-6698 requires the authenticated server matrix');
    const credentials = await LoginTestUtil.getTestCredentials();
    expect(credentials.user2).toBeDefined();
    const removalCounts = await verifyRemovalParity(browser, page, credentials.user2, false);
    expect(removalCounts).toEqual({ ownerRemovalCount: 2, observerRemovalCount: 2 });
  });
});
