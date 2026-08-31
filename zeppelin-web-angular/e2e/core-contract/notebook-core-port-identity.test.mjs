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
import { existsSync } from 'node:fs';
import { createReadStream } from 'node:fs';
import { createServer } from 'node:http';
import { extname, isAbsolute, join, relative, resolve } from 'node:path';
import { after, before, test } from 'node:test';
import { pathToFileURL } from 'node:url';

import { chromium, expect } from '@playwright/test';

const angularDistRoot = resolve('dist/notebook-core-port-proof');
const angularIndexPath = join(angularDistRoot, 'index.html');
const remoteEntryPath = join(angularDistRoot, 'assets/react/remoteEntry.js');

let browser;
let server;
let baseUrl;

const contentTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8']
]);

function resolveInsideAngularDist(requestPath) {
  const decodedPath = decodeURIComponent(requestPath.replace(/^\//, ''));
  const filePath = resolve(angularDistRoot, decodedPath);
  const rootRelativePath = relative(angularDistRoot, filePath);

  if (rootRelativePath.startsWith('..') || isAbsolute(rootRelativePath)) {
    return null;
  }

  return filePath;
}

function serveStaticFile(response, requestPath) {
  const filePath = resolveInsideAngularDist(requestPath);

  if (!filePath || !existsSync(filePath)) {
    response.writeHead(404);
    response.end('not found');
    return;
  }

  response.writeHead(200, {
    'cache-control': 'no-store',
    'content-type': contentTypes.get(extname(filePath)) ?? 'application/octet-stream'
  });
  createReadStream(filePath).pipe(response);
}

before(async () => {
  assert.ok(
    existsSync(angularIndexPath),
    `Angular host build output is missing: run "npm run build:notebook-core-port-proof" before this proof (${pathToFileURL(
      angularIndexPath
    )})`
  );
  assert.ok(
    existsSync(remoteEntryPath),
    `React remote asset is missing: run "npm run build:react && npm run build:notebook-core-port-proof" before this proof (${pathToFileURL(
      remoteEntryPath
    )})`
  );

  server = createServer((request, response) => {
    const requestPath = request.url?.split('?')[0] ?? '/';
    if (requestPath === '/') {
      response.writeHead(200, {
        'cache-control': 'no-store',
        'content-type': 'text/html; charset=utf-8'
      });
      createReadStream(angularIndexPath).pipe(response);
      return;
    }

    if (existsSync(resolveInsideAngularDist(requestPath) ?? '')) {
      serveStaticFile(response, requestPath);
      return;
    }

    response.writeHead(200, {
      'cache-control': 'no-store',
      'content-type': 'text/html; charset=utf-8'
    });
    createReadStream(angularIndexPath).pipe(response);
  });

  await new Promise(resolveListen => {
    server.listen(0, '127.0.0.1', resolveListen);
  });
  const address = server.address();
  assert.ok(address && typeof address === 'object');
  baseUrl = `http://127.0.0.1:${address.port}`;
  browser = await chromium.launch();
});

after(async () => {
  await browser?.close();
  await new Promise(resolveClose => server?.close(resolveClose));
});

test('React remote receives the exact host-owned NotebookCorePort object', async () => {
  const page = await browser.newPage();

  await page.goto(`${baseUrl}/#/core-contract/notebook-core-port`);

  const probe = page.getByTestId('notebook-core-port-probe');
  await expect(probe).toHaveAttribute('data-same-identity', 'true');
  await expect(probe).toHaveAttribute('data-note-id', 'note-host-owned');
  await expect(probe).toHaveAttribute('data-update-count', '0');

  const hostIdentity = await page.evaluate(() =>
    Object.is(
      globalThis.__zeppelinNotebookCorePortProof.hostCore,
      globalThis.__zeppelinNotebookCorePortProof.receivedCore
    )
  );
  assert.equal(hostIdentity, true);

  await page.getByTestId('publish-notebook-core-revision').click();

  await expect(probe).toHaveAttribute('data-revision-id', 'revision-from-angular-host');
  await expect(probe).toHaveAttribute('data-update-count', '1');

  const latestProof = await page.evaluate(() => globalThis.__zeppelinNotebookCorePortProof.proofs.at(-1));
  assert.deepEqual(latestProof, {
    sameIdentity: true,
    snapshot: { noteId: 'note-host-owned', revisionId: 'revision-from-angular-host' },
    updateCount: 1
  });

  await page.close();
});
