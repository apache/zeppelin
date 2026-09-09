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

import { execFileSync } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { cpus, platform, release, totalmem } from 'node:os';
import { resolve } from 'node:path';
import { expect, test, Page } from '@playwright/test';
import { NotebookPerformancePage } from '../models/notebook-performance-page';
import { addPageAnnotationBeforeEach, PAGES, waitForZeppelinReady } from '../utils';
import {
  fixturePath,
  loadFixture,
  measureBundles,
  Sample,
  summarize,
  validateFixture,
  validateServedIndex
} from './notebook-baseline';

// JUSTIFIED: The issue fixes this path outside tests/; the dedicated config isolates
// the benchmark from normal E2E runs and owns the deliberate cold/warm sequence.
test.describe('Notebook route performance contract', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK);

  test('records every cold and warm sample with readiness, FCP and build metadata', async ({
    browser,
    request,
    baseURL
  }, testInfo) => {
    const root = resolve(__dirname, '../..');
    const { note, sha256 } = loadFixture(root);
    const bundles = measureBundles(root);
    const dryRun = process.env.PERF_NOTEBOOK_DRY_RUN === '1';
    const runs = dryRun ? 1 : 10;
    const runId = randomUUID();
    const metadata = {
      schemaVersion: 1,
      runId,
      generatedAt: new Date().toISOString(),
      dryRun,
      zeppelinCommit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
      workingTreeDirty:
        execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim().length > 0,
      os: { platform: platform(), release: release() },
      cpuModel: cpus()[0].model,
      memoryBytes: totalmem(),
      browserVersion: browser.version(),
      nodeVersion: process.version,
      buildCommand: 'npm run build',
      fixture: { path: fixturePath, sha256 },
      baseURL,
      viewport: testInfo.project.use.viewport,
      runsPerCache: runs,
      warmPrimingNavigations: 1,
      percentileMethod: 'nearest-rank',
      bundles
    };

    await test.step('Given the built assets and the fixed imported note', async () => {
      const index = await request.get('/');
      expect(index.ok()).toBe(true);
      validateServedIndex(root, await index.text());
      for (const bundle of [...bundles.angularEntryChunks, bundles.reactRemoteEntry]) {
        const response = await request.get(`/${bundle.path.replace('dist/zeppelin/', '')}`);
        expect(response.ok()).toBe(true);
        expect(
          createHash('sha256')
            .update(await response.body())
            .digest('hex')
        ).toBe(bundle.sha256);
      }
    });

    const imported = await request.post('/api/notebook/import', {
      params: { notePath: `/__performance__/${runId}` },
      data: note
    });
    expect(imported.ok(), `Notebook import failed (${imported.status()}): ${await imported.text()}`).toBe(true);
    const noteId = (await imported.json()).body;
    expect(typeof noteId).toBe('string');
    expect(noteId.length).toBeGreaterThan(0);
    const samples: Sample[] = [];
    const contextOptions = {
      baseURL,
      storageState: testInfo.project.use.storageState,
      viewport: testInfo.project.use.viewport,
      userAgent: testInfo.project.use.userAgent,
      serviceWorkers: 'block' as const
    };
    const navigate = async (page: Page) => {
      await page.goto('about:blank');
      const navigation = await page.goto(`/#/notebook/${encodeURIComponent(noteId)}`, { waitUntil: 'commit' });
      const metrics = await new NotebookPerformancePage(page).measureReady();
      if (!navigation) {
        throw new Error('The benchmark requires a full document navigation response');
      }
      validateServedIndex(root, await navigation.text());
      await waitForZeppelinReady(page);
      return metrics;
    };
    try {
      const persisted = await request.get(`/api/notebook/${encodeURIComponent(noteId)}`);
      expect(persisted.ok()).toBe(true);
      validateFixture((await persisted.json()).body);

      await test.step('When each cold navigation uses a new context with an empty HTTP cache', async () => {
        for (let run = 1; run <= runs; run++) {
          const context = await browser.newContext(contextOptions);
          try {
            samples.push({ cache: 'cold', run, ...(await navigate(await context.newPage())) });
          } finally {
            await context.close();
          }
        }
      });

      await test.step('When warm navigations reuse one context after one unrecorded prime', async () => {
        const context = await browser.newContext(contextOptions);
        try {
          const page = await context.newPage();
          await navigate(page);
          for (let run = 1; run <= runs; run++) {
            samples.push({ cache: 'warm', run, ...(await navigate(page)) });
          }
        } finally {
          await context.close();
        }
      });

      await test.step('Then raw samples and median/p95 retain every measurement', async () => {
        expect(samples).toHaveLength(runs * 2);
        const summary = summarize(samples, runs);
        const output = resolve(root, 'e2e/performance/baselines');
        mkdirSync(output, { recursive: true });
        writeFileSync(resolve(output, `${runId}.raw.json`), `${JSON.stringify({ ...metadata, samples }, null, 2)}\n`);
        writeFileSync(
          resolve(output, `${runId}.summary.json`),
          `${JSON.stringify({ ...metadata, summary }, null, 2)}\n`
        );
        console.log(`Benchmark outputs: e2e/performance/baselines/${runId}.{raw,summary}.json`);
      });
    } finally {
      const deleted = await request.delete(`/api/notebook/${encodeURIComponent(noteId)}`);
      expect(deleted.ok()).toBe(true);
    }
  });
});
