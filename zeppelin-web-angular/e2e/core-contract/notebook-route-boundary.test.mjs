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

import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';

import { expect } from '@playwright/test';

import { startNotebookCoreProofHarness } from './proof-browser-harness.mjs';

let harness;
before(async () => {
  harness = await startNotebookCoreProofHarness();
});

after(async () => {
  await harness?.close();
});

test('Angular owns notebook route parsing and passes one port to the React remote', async () => {
  const page = await harness.browser.newPage();
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  // Angular's ErrorHandler reports errors thrown by production components through console.error('ERROR', ...)
  // instead of letting them reach pageerror.
  const angularErrors = [];
  page.on('console', message => {
    if (message.type() === 'error' && message.text().startsWith('ERROR')) {
      angularErrors.push(message.text());
    }
  });
  await page.goto(`${harness.baseUrl}/#/notebook/note-from-route`);

  await expect(page.locator('zeppelin-workspace')).toHaveCount(1);
  await expect(page.locator('zeppelin-notebook')).toHaveCount(1);

  const probe = page.getByTestId('notebook-core-port-probe');
  await expect(probe).toHaveAttribute('data-same-identity', 'true');
  await expect(probe).toHaveAttribute('data-note-id', 'note-from-route');
  await expect(probe).toHaveAttribute('data-revision-id', '');
  await expect(probe).toHaveAttribute('data-update-count', '0');

  await page.getByTestId('navigate-notebook-note').click();
  await expect(page).toHaveURL(`${harness.baseUrl}/#/notebook/note-route-updated`);
  await expect(probe).toHaveAttribute('data-note-id', 'note-route-updated');
  await expect(probe).toHaveAttribute('data-update-count', '1');

  await page.getByTestId('navigate-notebook-revision').click();
  await expect(page).toHaveURL(`${harness.baseUrl}/#/notebook/note-route-updated/revision/revision-from-route`);
  await expect(probe).toHaveAttribute('data-same-identity', 'true');
  await expect(probe).toHaveAttribute('data-note-id', 'note-route-updated');
  await expect(probe).toHaveAttribute('data-revision-id', 'revision-from-route');
  await expect(probe).toHaveAttribute('data-update-count', '2');
  await expect
    .poll(() =>
      page.evaluate(() =>
        globalThis.__zeppelinNotebookRouteBoundaryProof.messageCalls.some(
          call => call.method === 'noteRevision' && call.revisionId === 'revision-from-route'
        )
      )
    )
    .toBe(true);

  const routeProof = await page.evaluate(() => {
    const proofState = globalThis.__zeppelinNotebookRouteBoundaryProof;
    return {
      allReceivedPortsAreHostOwned: proofState.receivedCores.every(core => Object.is(core, proofState.hostCore)),
      activatedProductionNotebookComponents: proofState.activatedProductionNotebookComponents,
      connectionLifecycleCalls: proofState.connectionLifecycleCalls,
      hostPortIsFrozen: Object.isFrozen(proofState.hostCore),
      hostPortKeys: Object.keys(proofState.hostCore).sort(),
      latestPortIsHostOwned: Object.is(proofState.receivedCore, proofState.hostCore),
      messageCalls: proofState.messageCalls,
      receivedPortCount: new Set(proofState.receivedCores).size,
      routePaths: proofState.routePaths,
      snapshot: proofState.hostCore.getSnapshot(),
      workspaceGuardCalls: proofState.workspaceGuardCalls
    };
  });
  assert.deepEqual(routeProof, {
    allReceivedPortsAreHostOwned: true,
    activatedProductionNotebookComponents: [true, true, true],
    connectionLifecycleCalls: [],
    hostPortIsFrozen: true,
    hostPortKeys: ['getSnapshot', 'subscribe'],
    latestPortIsHostOwned: true,
    messageCalls: [
      { method: 'getNote', noteId: 'note-from-route' },
      { method: 'listRevisionHistory', noteId: 'note-from-route' },
      { method: 'getNote', noteId: 'note-route-updated' },
      { method: 'listRevisionHistory', noteId: 'note-route-updated' },
      { method: 'noteRevision', noteId: 'note-route-updated', revisionId: 'revision-from-route' },
      { method: 'listRevisionHistory', noteId: 'note-route-updated' }
    ],
    receivedPortCount: 1,
    routePaths: ['notebook/:noteId', 'notebook/:noteId/revision/:revisionId'],
    snapshot: { noteId: 'note-route-updated', revisionId: 'revision-from-route' },
    workspaceGuardCalls: ['/notebook/note-from-route']
  });
  assert.deepEqual(pageErrors, []);
  assert.deepEqual(angularErrors, []);

  await page.close();
});
