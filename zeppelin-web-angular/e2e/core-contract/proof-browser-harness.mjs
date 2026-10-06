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
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';

import { chromium } from '@playwright/test';

const angularDistRoot = resolve('dist/notebook-core-port-proof');
const angularIndexPath = join(angularDistRoot, 'index.html');
const remoteEntryPath = join(angularDistRoot, 'assets/react/remoteEntry.js');

const contentTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8']
]);

function resolveStaticFile(requestPath) {
  let decodedPath;
  try {
    decodedPath = decodeURIComponent(requestPath.replace(/^\//, ''));
  } catch {
    return null;
  }

  const filePath = resolve(angularDistRoot, decodedPath);
  const rootRelativePath = relative(angularDistRoot, filePath);
  const escapesRoot =
    rootRelativePath === '..' || rootRelativePath.startsWith(`..${sep}`) || isAbsolute(rootRelativePath);

  if (escapesRoot || !existsSync(filePath) || !statSync(filePath).isFile()) {
    return null;
  }

  return filePath;
}

function serveFile(response, filePath) {
  response.writeHead(200, {
    'cache-control': 'no-store',
    'content-type': contentTypes.get(extname(filePath)) ?? 'application/octet-stream'
  });
  createReadStream(filePath).pipe(response);
}

export async function startNotebookCoreProofHarness() {
  assert.ok(
    existsSync(angularIndexPath),
    `Angular host build output is missing: run "npm run build:notebook-core-port-proof" before this proof (${pathToFileURL(
      angularIndexPath
    )})`
  );
  assert.ok(
    existsSync(remoteEntryPath),
    `React remote asset is missing: run "npm run build:notebook-core-port-proof" before this proof (${pathToFileURL(
      remoteEntryPath
    )})`
  );

  const server = createServer((request, response) => {
    const requestPath = request.url?.split('?')[0] ?? '/';
    const staticFilePath = resolveStaticFile(requestPath);

    if (requestPath === '/') {
      serveFile(response, angularIndexPath);
      return;
    }

    if (staticFilePath) {
      serveFile(response, staticFilePath);
      return;
    }

    response.writeHead(404, { 'cache-control': 'no-store' });
    response.end();
  });

  await new Promise(resolveListen => {
    server.listen(0, '127.0.0.1', resolveListen);
  });
  const address = server.address();
  assert.ok(address && typeof address === 'object');
  let browser;
  try {
    browser = await chromium.launch();
  } catch (error) {
    await new Promise(resolveClose => server.close(resolveClose));
    throw error;
  }

  return {
    baseUrl: `http://127.0.0.1:${address.port}`,
    browser,
    close: async () => {
      await browser.close();
      await new Promise(resolveClose => server.close(resolveClose));
    }
  };
}
