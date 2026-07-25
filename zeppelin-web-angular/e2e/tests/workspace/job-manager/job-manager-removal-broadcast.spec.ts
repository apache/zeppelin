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

import { expect, test } from '@playwright/test';
import { JobManagerPage } from 'e2e/models/job-manager-page';
import {
  buildNoteJob,
  collectRuntimeErrors,
  expectNoNoteNameAccessError,
  JobManagerSocketStub,
  NoteJobPayload
} from 'e2e/models/job-manager-page.util';
import { addPageAnnotationBeforeEach, PAGES } from '../../../utils';

const ALPHA_NOTE_NAME = 'JobManagerRemovalAlpha';
const BETA_NOTE_NAME = 'JobManagerRemovalBeta';
const GAMMA_NOTE_NAME = 'JobManagerRemovalGamma';
const UNLISTED_NOTE_ID = 'notOwnedByThisViewer';

// ZEPPELIN-6551: deleting a note broadcasts a field-less removal stub to every Job Manager
// subscriber, while each viewer's own list is owner-filtered. For a note the viewer does not
// own the stub matched nothing in the list, got appended anyway, and filterJobs then threw on
// its missing noteName, leaving the page's filter and sort broken until re-navigation.
test.describe('Job Manager removal broadcast', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.JOB_MANAGER);

  let jobManagerPage: JobManagerPage;
  let socketStub: JobManagerSocketStub;
  let runtimeErrors: string[];
  let alphaJob: NoteJobPayload;

  test.beforeEach(async ({ page }) => {
    runtimeErrors = collectRuntimeErrors(page);

    alphaJob = buildNoteJob(ALPHA_NOTE_NAME);
    socketStub = new JobManagerSocketStub([alphaJob, buildNoteJob(BETA_NOTE_NAME)]);
    await socketStub.install(page);

    jobManagerPage = new JobManagerPage(page);
    await jobManagerPage.navigate();

    await expect(jobManagerPage.jobItems).toHaveCount(2);
  });

  test('Given a note absent from the list When its removal is broadcast Then no runtime error is raised', async () => {
    socketStub.broadcastRemoval(UNLISTED_NOTE_ID);

    // The stub carries no `noteName`, so rendering it would throw; it must not reach the list.
    await expect(jobManagerPage.jobItems).toHaveCount(2);
    expectNoNoteNameAccessError(runtimeErrors);
  });

  test('Given a note absent from the list When its removal is broadcast Then the note name filter keeps working', async () => {
    socketStub.broadcastRemoval(UNLISTED_NOTE_ID);
    await expect(jobManagerPage.jobItems).toHaveCount(2);

    // Before the fix the appended stub made every later `filterJobs` throw, so the rendered
    // list froze and stopped reacting to the search box.
    await jobManagerPage.filterByNoteName(ALPHA_NOTE_NAME);
    await expect(jobManagerPage.jobItems).toHaveCount(1);
    await expect(jobManagerPage.jobItemByName(ALPHA_NOTE_NAME)).toBeVisible();

    await jobManagerPage.clearNoteNameFilter();
    await expect(jobManagerPage.jobItems).toHaveCount(2);
    expectNoNoteNameAccessError(runtimeErrors);
  });

  test('Given a note absent from the list When its removal is broadcast Then no listed job is dropped', async () => {
    socketStub.broadcastRemoval(UNLISTED_NOTE_ID);

    // Guarding the removal inside the `currentJobIndex === -1` branch matters: folding the
    // guard into that condition would send the stub to the `else` branch and have it
    // `splice(-1, 1)` the last job out of the list.
    await expect(jobManagerPage.jobItemByName(ALPHA_NOTE_NAME)).toBeVisible();
    await expect(jobManagerPage.jobItemByName(BETA_NOTE_NAME)).toBeVisible();
  });

  test('Given a note present in the list When its removal is broadcast Then only that job is removed', async () => {
    socketStub.broadcastRemoval(alphaJob.noteId);

    await expect(jobManagerPage.jobItems).toHaveCount(1);
    await expect(jobManagerPage.jobItemByName(BETA_NOTE_NAME)).toBeVisible();
  });

  test('Given a note absent from the list When a running update is broadcast Then the job is added', async () => {
    // Only removal stubs are dropped; updates for notes the viewer has not seen yet still join the list.
    socketStub.broadcastUpdate([{ ...buildNoteJob(GAMMA_NOTE_NAME), isRunningJob: true }]);

    await expect(jobManagerPage.jobItems).toHaveCount(3);
    await expect(jobManagerPage.jobItemByName(GAMMA_NOTE_NAME)).toBeVisible();
  });
});
