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

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

import { expect, Page, test } from '@playwright/test';

import {
  createNotebookTransportRecorder,
  type TransportFixture,
  validateFixture
} from '../../../core-contract/notebook-transport-fixture.mjs';
import { E2E_TEST_FOLDER } from '../../../models/base-page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, PAGES, waitForZeppelinReady } from '../../../utils';

const fixtureLicense =
  'Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. ' +
  'See the NOTICE file distributed with this work for additional information regarding copyright ownership. ' +
  'The ASF licenses this file to You under the Apache License, Version 2.0.';
const terminalStatuses = new Set(['FINISHED', 'ERROR', 'ABORT']);

const captureProvenance = (
  page: Page,
  browserName: string,
  browserVersion: string,
  configuration: Record<string, boolean | number | string>
) => {
  const sourceCommit = process.env.ZEPPELIN_E2E_SOURCE_COMMIT;
  const baseCommit = process.env.ZEPPELIN_E2E_BASE_COMMIT;
  const buildManifestPath = process.env.ZEPPELIN_E2E_BUILD_MANIFEST;
  if (!sourceCommit) throw new Error('ZEPPELIN_E2E_SOURCE_COMMIT is required for live fixture capture');
  if (!baseCommit) throw new Error('ZEPPELIN_E2E_BASE_COMMIT is required for live fixture capture');
  if (!buildManifestPath) throw new Error('ZEPPELIN_E2E_BUILD_MANIFEST is required for live fixture capture');
  const manifest = JSON.parse(readFileSync(buildManifestPath, 'utf8'));
  if (manifest.sourceCommit !== sourceCommit) throw new Error('build manifest source does not match capture source');
  return {
    baseCommit,
    authentication: process.env.ZEPPELIN_E2E_CAPTURE_AUTHENTICATION ?? 'anonymous',
    browser: { name: browserName, version: browserVersion },
    buildManifest: {
      artifacts: manifest.artifacts,
      baseCommit: manifest.baseCommit,
      id: manifest.manifestId,
      launchTargets: manifest.launchTargets,
      sourceCommit: manifest.sourceCommit,
      sourceTree: manifest.sourceTree,
      version: manifest.version
    },
    captureMode: process.env.ZEPPELIN_E2E_CAPTURE_MODE ?? 'execution',
    configuration,
    interpreter: 'sh',
    isolation: {
      logs: '<capture-root>/logs',
      notebook: '<capture-root>/notebook',
      pid: '<capture-root>/run',
      recovery: '<capture-root>/recovery',
      root: '<capture-root>',
      searchIndex: '<capture-root>/index'
    },
    origin: new URL(page.url()).origin,
    sourceCommit
  };
};

interface ZeppelinResponse<T> {
  body?: T;
}

interface InterpreterSetting {
  group?: string;
  name?: string;
}

interface NoteResponse {
  id?: string;
  paragraphs?: Array<{ id?: string }>;
}

interface TicketResponse {
  principal: string;
  roles: string;
  ticket: string;
}

const websocketMessages = (fixture: TransportFixture, direction: 'send' | 'receive') =>
  fixture.records
    .filter(record => record.kind === 'websocket' && record.websocket?.direction === direction)
    .map(record => JSON.parse(record.websocket!.payloadText!) as { op: string; data?: Record<string, unknown> });

const createShellNotebook = async (page: Page): Promise<{ noteId: string; paragraphId: string }> => {
  const response = await page.request.post('/api/notebook', {
    data: {
      notePath: `${E2E_TEST_FOLDER}/ExecutionContractCapture_${Date.now()}`,
      addingEmptyParagraph: true,
      defaultInterpreterGroup: 'sh'
    },
    failOnStatusCode: false
  });
  if (!response.ok()) {
    throw new Error(`Create execution fixture notebook failed: ${response.status()} ${await response.text()}`);
  }
  const created = (await response.json()) as ZeppelinResponse<string>;
  if (!created.body) {
    throw new Error(`Create execution fixture notebook returned no id: ${JSON.stringify(created)}`);
  }

  const noteResponse = await page.request.get(`/api/notebook/${created.body}`, { failOnStatusCode: false });
  if (!noteResponse.ok()) {
    throw new Error(`Fetch execution fixture notebook failed: ${noteResponse.status()} ${await noteResponse.text()}`);
  }
  const note = (await noteResponse.json()) as ZeppelinResponse<NoteResponse>;
  const paragraphId = note.body?.paragraphs?.[0]?.id;
  if (!paragraphId) {
    throw new Error(`Execution fixture notebook returned no paragraph: ${JSON.stringify(note.body)}`);
  }
  return { noteId: created.body, paragraphId };
};

const deleteNotebook = async (page: Page, noteId: string): Promise<void> => {
  const response = await page.request.delete(`/api/notebook/${noteId}`, { failOnStatusCode: false });
  if (!response.ok()) {
    console.warn(`execution fixture note ${noteId} was not deleted; remove it manually`);
  }
};

const captureExecution = async (
  page: Page,
  browserName: string,
  browserVersion: string,
  noteId: string,
  paragraphId: string,
  streaming: boolean,
  cancel: boolean
): Promise<TransportFixture> => {
  const ticketResponse = await page.request.get('/api/security/ticket');
  const ticketJson = (await ticketResponse.json()) as ZeppelinResponse<TicketResponse> & TicketResponse;
  const ticket = ticketJson.body ?? ticketJson;
  const versionResponse = await page.request.get('/api/version');
  const versionJson = (await versionResponse.json()) as ZeppelinResponse<{ version?: string }> & { version?: string };
  const zeppelinVersion = versionJson.body?.version ?? versionJson.version ?? 'unknown';
  const code = cancel ? '%sh\nsleep 30' : "%sh\nprintf 'first\\n'\nsleep 1\nprintf 'second\\n'";
  const recorder = createNotebookTransportRecorder({
    captureSource: 'live-server',
    capturedAt: new Date().toISOString(),
    configuration: { 'zeppelin.websocket.paragraph_status_progress.enable': streaming },
    // GET_NOTE is the mandatory first frame. The completed fixture replaces this
    // bootstrap value with the operations actually observed in both directions.
    coveredOperations: ['GET_NOTE'],
    interpreter: 'sh',
    knownExclusions: [],
    owner: 'zeppelin-web-angular',
    provenance: captureProvenance(page, browserName, browserVersion, {
      'zeppelin.websocket.paragraph_status_progress.enable': streaming
    }),
    scenario: cancel
      ? 'Cancel a running paragraph'
      : `Run a paragraph with status and output streaming ${streaming ? 'enabled' : 'disabled'}`,
    zeppelinVersion
  });
  const recorderPage = await page.context().newPage();
  try {
    await recorderPage.goto('/');
    await waitForZeppelinReady(recorderPage);
    recorder.install(recorderPage);
    await recorderPage.evaluate(
      ({ captureTicket, codeToRun, note, paragraph, shouldCancel }) =>
        new Promise<void>((resolvePromise, rejectPromise) => {
          const locationUrl = new URL(window.location.href);
          locationUrl.protocol = locationUrl.protocol === 'https:' ? 'wss:' : 'ws:';
          locationUrl.pathname = `${locationUrl.pathname.replace(/\/$/, '')}/ws`;
          locationUrl.hash = '';
          locationUrl.search = '';
          const socket = new WebSocket(locationUrl);
          let messageSequence = 0;
          let runSent = false;
          let cancelSent = false;
          let terminalSeen = false;
          const timeout = window.setTimeout(
            () => {
              socket.close();
              rejectPromise(new Error(`Execution fixture capture timed out for paragraph ${paragraph}`));
            },
            shouldCancel ? 120000 : 60000
          );
          const send = (op: string, data: Record<string, unknown>) => {
            socket.send(
              JSON.stringify({
                op,
                msgId: `execution-capture-${++messageSequence}`,
                data,
                ...captureTicket
              })
            );
          };

          socket.onerror = () => rejectPromise(new Error('Execution fixture WebSocket failed'));
          socket.onclose = () => {
            if (!terminalSeen) rejectPromise(new Error('Execution fixture WebSocket closed before terminal PARAGRAPH'));
          };
          socket.onopen = () => send('GET_NOTE', { id: note });
          socket.onmessage = event => {
            const message = JSON.parse(String(event.data)) as {
              op?: string;
              data?: { note?: { id?: string }; paragraph?: { id?: string; status?: string } };
            };
            if (message.op === 'NOTE' && message.data?.note?.id === note && !runSent) {
              runSent = true;
              send('RUN_PARAGRAPH', { id: paragraph, title: '', paragraph: codeToRun, config: {}, params: {} });
              return;
            }
            const target = message.data?.paragraph;
            if (shouldCancel && target?.id === paragraph && target.status === 'RUNNING' && !cancelSent) {
              cancelSent = true;
              send('CANCEL_PARAGRAPH', { id: paragraph });
              return;
            }
            if (target?.id === paragraph && target.status && ['FINISHED', 'ERROR', 'ABORT'].includes(target.status)) {
              terminalSeen = true;
              window.clearTimeout(timeout);
              window.setTimeout(() => {
                socket.close();
                resolvePromise();
              }, 250);
            }
          };
        }),
      { captureTicket: ticket, codeToRun: code, note: noteId, paragraph: paragraphId, shouldCancel: cancel }
    );
    await recorder.stop();
    const fixture = recorder.snapshot();
    fixture.metadata!.coveredOperations = [
      ...new Set(
        websocketMessages(fixture, 'send')
          .concat(websocketMessages(fixture, 'receive'))
          .map(message => message.op)
      )
    ];
    return fixture;
  } finally {
    await recorder.stop().catch(() => undefined);
    await recorderPage.close();
  }
};

const writeFixture = (name: string, fixture: TransportFixture): void => {
  if (process.env.ZEPPELIN_WRITE_EXECUTION_FIXTURES !== '1') return;
  const directory = process.env.ZEPPELIN_E2E_FIXTURE_OUTPUT_DIR ?? resolve('e2e/core-contract/fixtures');
  const output = resolve(directory, `${name}.json`);
  mkdirSync(dirname(output), { recursive: true });
  writeFileSync(output, `${JSON.stringify({ _license: fixtureLicense, ...fixture }, null, 2)}\n`);
};

test.describe('Notebook execution transport capture', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

  test(
    'captures run, progress, streaming output and terminal delivery',
    { tag: '@live' },
    async ({ browserName, page }) => {
      const streamingValue = process.env.ZEPPELIN_CAPTURE_EXPECT_STREAMING;
      test.skip(
        streamingValue !== 'true' && streamingValue !== 'false',
        'ZEPPELIN-6671 live execution capture requires ZEPPELIN_CAPTURE_EXPECT_STREAMING=true or false'
      );
      const streaming = streamingValue === 'true';

      await page.goto('/#/');
      await waitForZeppelinReady(page);
      await performLoginIfRequired(page);
      const settingsResponse = await page.request.get('/api/interpreter/setting', { failOnStatusCode: false });
      const settingsJson = (await settingsResponse.json()) as ZeppelinResponse<InterpreterSetting[]>;
      const shellAvailable = settingsResponse.ok() && settingsJson.body?.some(setting => setting.name === 'sh');
      test.skip(!shellAvailable, 'ZEPPELIN-6671 requires the named sh interpreter setting');

      const { noteId, paragraphId } = await createShellNotebook(page);
      try {
        const fixture = await captureExecution(
          page,
          browserName,
          page.context().browser()!.version(),
          noteId,
          paragraphId,
          streaming,
          false
        );
        expect(validateFixture(fixture)).toEqual([]);
        const received = websocketMessages(fixture, 'receive');
        const executionOperations = received.filter(message =>
          ['PROGRESS', 'PARAGRAPH_UPDATE_OUTPUT', 'PARAGRAPH_APPEND_OUTPUT', 'PARAGRAPH'].includes(message.op)
        );
        const terminal = [...executionOperations]
          .reverse()
          .find(message => terminalStatuses.has(String((message.data?.paragraph as { status?: string })?.status)));
        expect(terminal).toBeDefined();
        const executionOperationNames = executionOperations.map(message => message.op);
        expect({
          append: executionOperationNames.includes('PARAGRAPH_APPEND_OUTPUT'),
          progress: executionOperationNames.includes('PROGRESS'),
          update: executionOperationNames.includes('PARAGRAPH_UPDATE_OUTPUT')
        }).toEqual({ append: streaming, progress: streaming, update: streaming });
        writeFixture(streaming ? 'execution-streaming-enabled' : 'execution-streaming-disabled', fixture);
      } finally {
        await deleteNotebook(page, noteId);
      }
    }
  );

  test('captures explicit cancellation through terminal ABORT', { tag: '@live' }, async ({ browserName, page }) => {
    test.skip(
      process.env.ZEPPELIN_CAPTURE_EXPECT_STREAMING !== 'true',
      'ZEPPELIN-6671 cancellation capture runs on the streaming-enabled fixture server'
    );
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
    const settingsResponse = await page.request.get('/api/interpreter/setting', { failOnStatusCode: false });
    const settingsJson = (await settingsResponse.json()) as ZeppelinResponse<InterpreterSetting[]>;
    const shellAvailable = settingsResponse.ok() && settingsJson.body?.some(setting => setting.name === 'sh');
    test.skip(!shellAvailable, 'ZEPPELIN-6671 requires the named sh interpreter setting');

    const { noteId, paragraphId } = await createShellNotebook(page);
    try {
      const fixture = await captureExecution(
        page,
        browserName,
        page.context().browser()!.version(),
        noteId,
        paragraphId,
        true,
        true
      );
      expect(validateFixture(fixture)).toEqual([]);
      expect(websocketMessages(fixture, 'send').map(message => message.op)).toEqual(
        expect.arrayContaining(['RUN_PARAGRAPH', 'CANCEL_PARAGRAPH'])
      );
      const terminal = websocketMessages(fixture, 'receive')
        .filter(message => message.op === 'PARAGRAPH')
        .reverse()
        .find(message => terminalStatuses.has(String((message.data?.paragraph as { status?: string })?.status)));
      expect((terminal?.data?.paragraph as { status?: string })?.status).toBe('ABORT');
      writeFixture('execution-cancel', fixture);
    } finally {
      await deleteNotebook(page, noteId);
    }
  });
});
