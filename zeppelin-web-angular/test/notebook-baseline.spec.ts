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

import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import {
  fixturePath,
  loadFixture,
  measureBundles,
  Sample,
  summarize,
  validateFixture,
  validateServedIndex
} from '../e2e/performance/notebook-baseline';

describe('Notebook benchmark contract', () => {
  const temporaryRoots: string[] = [];
  const tempRoot = () => {
    const root = mkdtempSync(resolve(tmpdir(), 'zeppelin-perf-contract-'));
    temporaryRoots.push(root);
    return root;
  };
  afterEach(() => temporaryRoots.splice(0).forEach(root => rmSync(root, { recursive: true, force: true })));

  it('accepts the versioned fixture and rejects changed bytes against its committed hash', () => {
    const { note } = loadFixture(process.cwd());
    expect(note.paragraphs).toHaveLength(100);
    expect(new Set(note.paragraphs.map((paragraph: { status: string }) => paragraph.status))).toEqual(
      new Set(['READY'])
    );
    const root = tempRoot();
    mkdirSync(dirname(resolve(root, fixturePath)), { recursive: true });
    writeFileSync(resolve(root, fixturePath), `${readFileSync(fixturePath, 'utf8')}\n`);
    expect(() => loadFixture(root)).toThrow('SHA-256 mismatch');
  });

  it.each(['missing', 'duplicate', 'result', 'expanded', 'running'])(
    'rejects the %s fixture contract mutation',
    mutation => {
      const { note } = loadFixture(process.cwd());
      if (mutation === 'missing') note.paragraphs.pop();
      if (mutation === 'duplicate') note.paragraphs[1].id = note.paragraphs[0].id;
      if (mutation === 'result') note.paragraphs[80].results.msg[0].type = 'TEXT';
      if (mutation === 'expanded') note.paragraphs[90].config.editorHide = false;
      if (mutation === 'running') note.paragraphs[0].status = 'RUNNING';
      expect(() => validateFixture(note)).toThrow('Benchmark fixture');
    }
  );

  it('retains the outlier and computes median and nearest-rank p95 separately by cache mode', () => {
    const samples: Sample[] = (['cold', 'warm'] as const).flatMap(cache =>
      Array.from({ length: 10 }, (_, index) => ({
        cache,
        run: index + 1,
        notebookReadyMs: index === 9 ? 1000 : index + 1,
        fcpMs: index + 1
      }))
    );
    expect(summarize(samples, 10)).toEqual({
      cold: { count: 10, notebookReadyMs: { median: 5.5, p95: 1000 }, fcpMs: { median: 5.5, p95: 10 } },
      warm: { count: 10, notebookReadyMs: { median: 5.5, p95: 1000 }, fcpMs: { median: 5.5, p95: 10 } }
    });
    expect(samples).toHaveLength(20);
  });

  it('rejects a missing run or metric instead of publishing an incomplete summary', () => {
    const cold: Sample = { cache: 'cold', run: 1, notebookReadyMs: 10, fcpMs: 5 };
    expect(() => summarize([cold], 1)).toThrow('warm samples');
    expect(() => summarize([cold, { ...cold, cache: 'warm', fcpMs: NaN }], 1)).toThrow('Missing or invalid fcpMs');
    expect(summarize([cold, { ...cold, cache: 'warm' }], 1).cold).toEqual({
      count: 1,
      notebookReadyMs: { median: 10, p95: 10 },
      fcpMs: { median: 5, p95: 5 }
    });
  });

  it('rejects an old served index even when both builds remain available, while allowing server-injected markup', () => {
    const root = tempRoot();
    const dist = resolve(root, 'dist/zeppelin');
    mkdirSync(dist, { recursive: true });
    const current = '<script src="runtime.new.js"></script><script src="main.new.js"></script>';
    writeFileSync(resolve(dist, 'index.html'), current);
    for (const name of ['runtime.new.js', 'main.new.js', 'runtime.old.js', 'main.old.js']) {
      writeFileSync(resolve(dist, name), name);
    }
    expect(() => validateServedIndex(root, current.replaceAll('.new.js', '.old.js'))).toThrow(
      'Served Angular entry scripts do not match the local build'
    );
    expect(() =>
      validateServedIndex(root, `<base href="/"><script>window.injected = true;</script>${current}`)
    ).not.toThrow();
  });

  it('measures only Angular index entry scripts and the separate React entry, and fails if the latter is missing', () => {
    const root = tempRoot();
    const dist = resolve(root, 'dist/zeppelin');
    mkdirSync(resolve(dist, 'assets/react'), { recursive: true });
    writeFileSync(
      resolve(dist, 'index.html'),
      '<script src="runtime.abc.js"></script><script src="main.abc.js" type="module"></script>'
    );
    writeFileSync(resolve(dist, 'runtime.abc.js'), 'runtime');
    writeFileSync(resolve(dist, 'main.abc.js'), 'application');
    writeFileSync(resolve(dist, 'lazy.js'), 'unrelated lazy chunk');
    writeFileSync(resolve(dist, 'assets/react/remoteEntry.js'), 'remote');
    const result = measureBundles(root);
    expect(result.angularEntryChunks.map(chunk => chunk.path)).toEqual([
      'dist/zeppelin/runtime.abc.js',
      'dist/zeppelin/main.abc.js'
    ]);
    expect(result.angularEntryChunks.map(chunk => chunk.rawBytes)).toEqual([7, 11]);
    expect(result.reactRemoteEntry.rawBytes).toBe(6);
    expect(result.reactRemoteEntry.gzipBytes).toBeGreaterThan(0);
    rmSync(resolve(dist, 'assets/react/remoteEntry.js'));
    expect(() => measureBundles(root)).toThrow();
  });
});
