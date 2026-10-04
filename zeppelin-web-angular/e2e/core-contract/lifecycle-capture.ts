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
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { expect, type Page, type BrowserContext, type TestInfo } from '@playwright/test';
import type { LifecycleRecorder } from './notebook-lifecycle-fixture.mjs';
export const lifecycleFixtureDirectory = () =>
  process.env.ZEPPELIN_LIFECYCLE_FIXTURE_DIR ?? path.resolve('e2e/fixtures/notebook-lifecycle');
const metadata = (scenario: string, operations: string[]) => ({
  owner: 'zeppelin-web-angular',
  scenario,
  coveredOperations: operations,
  knownExclusions: [
    'Shared Notebook Core runtime convergence is gated by ZEPPELIN-6687; this capture proves Angular and wire behavior.',
    'Physical reconnect backoff and terminal teardown are owned by ZEPPELIN-6696.',
    'Untagged broadcasts do not identify a note; route context is evidence, not a wire acknowledgement.'
  ]
});

export const captureMetadata = async (page: Page, scenario: string, operations: string[]) => {
  const expected = process.env.ZEPPELIN_CAPTURE_MASTER_COMMIT;
  if (!expected || !/^[a-f0-9]{40}$/.test(expected)) {
    throw new Error('Live baseline requires ZEPPELIN_CAPTURE_MASTER_COMMIT from the verified Apache master ref');
  }
  const response = await page.request.get('/api/version');
  const version = (await response.json()).body as { 'git-commit-id': string; version: string };
  expect(version['git-commit-id']).toMatch(/^[a-f0-9]{7,40}$/);
  expect(expected.startsWith(version['git-commit-id'])).toBe(true);
  return {
    ...metadata(scenario, operations),
    capturedAt: new Date().toISOString(),
    zeppelinVersion: version.version,
    source: { repository: 'apache/zeppelin', commit: expected, serverCommit: version['git-commit-id'] }
  };
};

export const recordCaptureResult = (info: TestInfo, entry: { file: string; operations: string[] }) => {
  const directory = lifecycleFixtureDirectory();
  const file = path.join(directory, 'manifest.json');
  mkdirSync(directory, { recursive: true });

  const manifest = existsSync(file)
    ? (JSON.parse(readFileSync(file, 'utf8')) as { fixtures: Record<string, unknown>[] })
    : { fixtures: [] };

  manifest.fixtures = manifest.fixtures.filter(value => value.file !== entry.file);
  manifest.fixtures.push({
    ...entry,
    status: info.status === 'passed' ? 'supported' : info.status === 'skipped' ? 'skipped' : 'failed',
    ...(info.status === 'skipped'
      ? { reason: info.annotations.find(value => value.type === 'skip')?.description }
      : {}),
    ...(info.status === 'failed' ? { reason: 'Live scenario failed; this capture does not establish coverage.' } : {})
  });

  writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
};

export const cleanUpLifecycleCapture = async (
  page: Page,
  recorder: LifecycleRecorder,
  noteIds: string[],
  contexts: BrowserContext[] = []
) => {
  const results = await Promise.allSettled([
    recorder.stop(),
    ...contexts.map(context => context.close()),
    ...noteIds.map(async id => {
      const response = await page.request.delete(`/api/notebook/${id}`);
      expect(response.ok()).toBe(true);
    })
  ]);

  const failures = results.filter((result): result is PromiseRejectedResult => result.status === 'rejected');
  if (failures.length) {
    throw new AggregateError(
      failures.map(result => result.reason),
      'Lifecycle capture cleanup failed'
    );
  }
};
