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
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { createLifecycleRecorder, type LifecycleFault } from '../../../core-contract/notebook-lifecycle-fixture.mjs';
import {
  captureMetadata,
  cleanUpLifecycleCapture,
  lifecycleFixtureDirectory,
  recordCaptureResult
} from '../../../core-contract/lifecycle-capture';
import { replayLifecycleTrace } from '../../../core-contract/replay-lifecycle-trace';
import { NotebookTransportPage } from '../../../models/notebook-transport-page';
import { openTransportNote, waitForTransportReply } from '../../../models/notebook-transport-page.util';
import { NotebookKeyboardPage } from '../../../models/notebook-keyboard-page';
import { installCommitParagraphProbe } from '../../../models/notebook-save-timing.util';
import { addPageAnnotationBeforeEach, PAGES, waitForZeppelinReady } from '../../../utils';

const observationDeadlineMs = 250;
const originalText = '%md server baseline';
const draftText = '%md local draft';
const operations = ['COMMIT_PARAGRAPH', 'GET_NOTE', 'NOTE', 'GET /api/notebook/{noteId}'];
const lossScenarios = [
  { loss: 'request', file: 'commit-request-loss.json', canonicalText: originalText },
  { loss: 'reply', file: 'commit-reply-loss.json', canonicalText: draftText }
] as const;

test.describe('Notebook commit loss transport evidence', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);
  let view: NotebookTransportPage;
  let editor: NotebookKeyboardPage;

  test.beforeEach(async ({ page }) => {
    view = new NotebookTransportPage(page);
    editor = new NotebookKeyboardPage(page);
  });

  test.afterEach(async ({}, info) => {
    const scenario = lossScenarios.find(value => info.title.includes(`commit ${value.loss} loss`));
    if (scenario) {
      recordCaptureResult(info, { file: scenario.file, operations });
    }
  });

  for (const scenario of lossScenarios) {
    test(
      `captures commit ${scenario.loss} loss and targeted reconciliation`,
      { tag: '@live' },
      async ({ page, browser }) => {
        const probe = await installCommitParagraphProbe(page);
        await view.install();
        if (scenario.loss === 'request') {
          probe.dropFirstCommitParagraph();
        } else {
          probe.dropFirstCommitParagraphResponse();
        }

        await page.goto('/#/');
        await waitForZeppelinReady(page);
        const metadata = {
          ...(await captureMetadata(page, `Commit ${scenario.loss} loss`, operations)),
          commitLoss: {
            loss: scenario.loss,
            faults: [] as LifecycleFault[],
            observationDeadlineMs,
            localDraft: draftText,
            canonicalText: scenario.canonicalText,
            reconciliation: 'GET /api/notebook/{noteId}, then GET_NOTE / NOTE',
            protocolGap: 'Missing correlated PARAGRAPH alone cannot distinguish request loss from reply loss.',
            lifecycleGate:
              'Angular has no automatic save acknowledgement timeout; reconciliation here is an explicit test action.'
          }
        };
        const recorder = createLifecycleRecorder(metadata);
        const noteIds: string[] = [];
        let recording = false;
        try {
          const created = await page.request.post('/api/notebook', {
            data: {
              notePath: `LifecycleCapture/commit-${scenario.loss}-${Date.now()}`,
              paragraphs: [{ text: originalText }]
            }
          });
          expect(created.ok()).toBe(true);
          const noteId = (await created.json()).body as string;
          noteIds.push(noteId);
          recorder.install(page, 'viewer-a');
          recording = true;
          probe.onDroppedCommit(message => recorder.droppedSend('viewer-a', message.toString()));

          await test.step('Given a live editor whose next commit or correlated reply is dropped', async () => {
            await page.reload();
            recorder.context('viewer-a', { state: 'active', noteId, revisionId: null });
            await openTransportNote(view, noteId);
            await editor.firstEditorInput.focus();
            await editor.pressSelectAll();
            await page.keyboard.insertText(draftText);
            await expect(view.editorText.first()).toContainText('local draft');
          });

          await test.step('When the observation deadline expires without a correlated save reply', async () => {
            const [commit] = await probe.waitForCommitCount(1);
            expect(commit.data.paragraph).toBe(draftText);
            await probe.expectNoForwardedResponse(commit.msgId, observationDeadlineMs);
            await expect(view.editorText.first()).toContainText('local draft');
            await expect
              .poll(async () => (await view.canonical(noteId)).paragraphs[0].text)
              .toBe(scenario.canonicalText);
          });

          await test.step('Then targeted refetch reveals the server-confirmed text independently of the draft', async () => {
            await waitForTransportReply(view, 'NOTE', await view.send('GET_NOTE', { id: noteId }));
            await expect(view.editorText.first()).toContainText(scenario.canonicalText.slice(4));
            await recorder.stop();
            if (scenario.loss === 'reply') {
              const capture = recorder.snapshot();
              const sent = capture.records.find(
                record =>
                  record.kind === 'websocket' &&
                  record.websocket.direction === 'send' &&
                  JSON.parse(record.websocket.payloadText).op === 'COMMIT_PARAGRAPH'
              );
              if (!sent || sent.kind !== 'websocket') throw new Error('Commit request was not captured');
              const reply = capture.records.find(
                record =>
                  record.kind === 'websocket' &&
                  record.websocket.direction === 'receive' &&
                  JSON.parse(record.websocket.payloadText).msgId === JSON.parse(sent.websocket.payloadText).msgId &&
                  JSON.parse(record.websocket.payloadText).op === 'PARAGRAPH'
              );
              if (!reply) throw new Error('Real correlated save reply was not captured');
              metadata.commitLoss.faults.push({ sequence: reply.sequence, copies: 0 });
            }

            const [commit] = await probe.waitForCommitCount(1);
            expect(probe.forwardedResponseCount(commit.msgId)).toBe(0);

            const fixture = await recorder.write(path.join(lifecycleFixtureDirectory(), scenario.file));
            const frames = await replayLifecycleTrace(browser, fixture, metadata.commitLoss.faults);
            const note = frames.get('viewer-a')!.findLast(frame => frame.op === 'NOTE')!.data as {
              note: { paragraphs: { text: string }[] };
            };
            expect(note.note.paragraphs[0].text).toBe(scenario.canonicalText);
          });
        } finally {
          await cleanUpLifecycleCapture(page, recording ? recorder : undefined, noteIds);
        }
      }
    );
  }
});
