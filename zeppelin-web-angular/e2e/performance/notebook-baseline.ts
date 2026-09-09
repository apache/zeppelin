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

import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve, relative } from 'node:path';
import { gzipSync } from 'node:zlib';

export const fixturePath = 'e2e/fixtures/performance/notebook-route-100-paragraphs.json';
const fixtureSha256 = '7f94de1f9a7e529c7baf473a8bba9ab017f410c896f21bd8357922b63bd81fde';
export type Sample = { cache: 'cold' | 'warm'; run: number; notebookReadyMs: number; fcpMs: number };

export const loadFixture = (root: string) => {
  const bytes = readFileSync(resolve(root, fixturePath));
  const sha256 = createHash('sha256').update(bytes).digest('hex');
  if (sha256 !== fixtureSha256) {
    throw new Error('Benchmark fixture SHA-256 mismatch');
  }
  const note = JSON.parse(bytes.toString('utf8'));
  validateFixture(note);
  return { note, sha256 };
};

export const validateFixture = (note: {
  paragraphs: Array<{
    id: string;
    status: string;
    config: { editorHide: boolean; tableHide: boolean };
    results?: { code: string; msg: Array<{ type: string; data: string }> };
  }>;
}): void => {
  const paragraphs = note.paragraphs;
  if (!Array.isArray(paragraphs) || paragraphs.length !== 100 || new Set(paragraphs.map(p => p.id)).size !== 100) {
    throw new Error('Benchmark fixture requires 100 distinct paragraphs');
  }
  paragraphs.forEach((p, index) => {
    const collapsed = index >= 90;
    const result = p.results?.msg;
    if (
      p.config.editorHide !== collapsed ||
      p.config.tableHide !== collapsed ||
      p.status !== 'READY' ||
      (collapsed
        ? p.results !== undefined
        : p.results?.code !== 'SUCCESS' ||
          result?.length !== 1 ||
          result[0].type !== (index < 80 ? 'TEXT' : 'TABLE') ||
          !result[0].data)
    ) {
      throw new Error(
        `Benchmark fixture paragraph ${index + 1} violates the 80 text / 10 table / 10 collapsed contract`
      );
    }
  });
};

export const summarize = (samples: Sample[], runs: number) => {
  return Object.fromEntries(
    (['cold', 'warm'] as const).map(cache => {
      const group = samples.filter(sample => sample.cache === cache);
      if (group.length !== runs || group.some((sample, index) => sample.run !== index + 1)) {
        throw new Error(`Expected exactly ${runs} ordered ${cache} samples`);
      }
      const metrics = Object.fromEntries(
        (['notebookReadyMs', 'fcpMs'] as const).map(metric => {
          const values = group.map(sample => sample[metric]).sort((a, b) => a - b);
          if (values.some(value => !Number.isFinite(value) || value <= 0)) {
            throw new Error(`Missing or invalid ${metric}`);
          }
          const middle = Math.floor(values.length / 2);
          return [
            metric,
            {
              median: values.length % 2 ? values[middle] : (values[middle - 1] + values[middle]) / 2,
              p95: values[Math.ceil(values.length * 0.95) - 1]
            }
          ];
        })
      );
      return [cache, { count: group.length, ...metrics }];
    })
  );
};

const angularEntryScripts = (index: string): string[] => {
  const scripts = [...index.matchAll(/<script\b[^>]*\bsrc=["']([^"']+)["'][^>]*>/gi)].map(match => match[1]);
  if (!scripts.length || !scripts.some(script => /^main[.-].*\.js$/.test(script))) {
    throw new Error('Build output must contain Angular application entry scripts; run npm run build');
  }
  for (const script of scripts) {
    if (!/^[\w.-]+\.js$/.test(script)) {
      throw new Error(`Unexpected Angular entry script: ${script}`);
    }
  }
  return scripts;
};

export const validateServedIndex = (root: string, servedIndex: string): void => {
  const expected = angularEntryScripts(readFileSync(resolve(root, 'dist/zeppelin/index.html'), 'utf8'));
  const actual = angularEntryScripts(servedIndex);
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`Served Angular entry scripts do not match the local build: ${JSON.stringify(actual)}`);
  }
};

export const measureBundles = (root: string) => {
  const angularRoot = resolve(root, 'dist/zeppelin');
  const scripts = angularEntryScripts(readFileSync(resolve(angularRoot, 'index.html'), 'utf8'));
  const measure = (path: string) => {
    const bytes = readFileSync(path);
    return {
      path: relative(root, path),
      rawBytes: bytes.length,
      gzipBytes: gzipSync(bytes).length,
      sha256: createHash('sha256').update(bytes).digest('hex')
    };
  };
  const angularEntryChunks = [...new Set(scripts)].map(script => {
    return measure(resolve(angularRoot, script));
  });
  return { angularEntryChunks, reactRemoteEntry: measure(resolve(angularRoot, 'assets/react/remoteEntry.js')) };
};
