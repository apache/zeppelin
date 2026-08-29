/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { dirname, join } from 'path';
import { afterEach, describe, expect, it } from 'vitest';
import CoverageReporter from '../e2e/reporter.coverage';
import cfg from '../e2e/reporter.coverage.config';
import { COVERAGE_EXCLUDED_COMPONENTS, flattenPageComponents, getCoverageTransformPaths, PAGES } from '../e2e/utils';

const fixtureRoots: string[] = [];

const writeFixture = (rootPath: string, relativePath: string) => {
  const filePath = join(rootPath, relativePath);
  mkdirSync(dirname(filePath), { recursive: true });
  writeFileSync(filePath, '');
};

afterEach(() => {
  fixtureRoots.splice(0).forEach(rootPath => rmSync(rootPath, { recursive: true, force: true }));
});

describe('CoverageReporter', () => {
  it('uses the configured coverage targets as its denominator', async () => {
    const reporter = new CoverageReporter();

    await reporter.onBegin();

    expect(reporter.targetPaths).toEqual(cfg.transform);
  });

  it('discovers component additions and deletions while honoring exclusions', () => {
    const rootPath = mkdtempSync(join(tmpdir(), 'zeppelin-e2e-coverage-'));
    fixtureRoots.push(rootPath);
    writeFixture(rootPath, 'src/app/included/included.component.ts');
    writeFixture(rootPath, 'src/app/excluded/excluded.component.ts');
    writeFixture(rootPath, 'src/app/ignored/ignored.service.ts');

    expect(getCoverageTransformPaths(rootPath, ['src/app/excluded/excluded.component'])).toEqual([
      'src/app/included/included.component'
    ]);

    rmSync(join(rootPath, 'src/app/included/included.component.ts'));

    expect(getCoverageTransformPaths(rootPath, ['src/app/excluded/excluded.component'])).toEqual([]);
  });

  it('keeps annotations and exclusions aligned with existing component files', () => {
    const pagePaths = flattenPageComponents(PAGES);
    const packageRoot = join(__dirname, '..');

    expect(pagePaths).toEqual(cfg.transform);
    expect(new Set(pagePaths).size).toBe(pagePaths.length);
    expect(new Set(COVERAGE_EXCLUDED_COMPONENTS).size).toBe(COVERAGE_EXCLUDED_COMPONENTS.length);
    COVERAGE_EXCLUDED_COMPONENTS.forEach(componentPath => {
      expect(existsSync(join(packageRoot, `${componentPath}.ts`))).toBe(true);
    });
  });
});
