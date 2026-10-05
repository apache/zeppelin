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
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

export const lifecycleCoreReference = Object.freeze({
  repository: 'voidmatcha/zeppelin',
  commit: '36b5f356c63413e43c26ebcd448af91b0f353ba2'
});

const frontend = fileURLToPath(new URL('../../../../', import.meta.url));
const corePath = 'zeppelin-web-angular/projects/zeppelin-notebook-core/src';
const adapterPath = 'zeppelin-web-angular/src/app/pages/workspace/notebook/notebook-core-route.adapter.ts';
const git = (root, ...args) => execFileSync('git', ['-C', root, ...args], { encoding: 'utf8' }).trim();

/** @param {string} root */
export function verifyLifecycleCoreReference(root) {
  if (git(root, 'rev-parse', 'HEAD') !== lifecycleCoreReference.commit) {
    throw new Error(`Core reference must be checked out at ${lifecycleCoreReference.commit}`);
  }
  if (git(root, 'diff', 'HEAD', '--', corePath, adapterPath)) {
    throw new Error('Core reference sources must match the pinned commit without modifications');
  }
  const files = git(root, 'ls-files', '--', corePath, adapterPath).split('\n');
  const sources = Object.fromEntries(
    files.map(file => [
      file,
      createHash('sha256')
        .update(readFileSync(path.join(root, file)))
        .digest('hex')
    ])
  );
  return { ...lifecycleCoreReference, sources };
}

/** @param {string} root */
export async function buildLifecycleCoreReference(root) {
  const provenance = verifyLifecycleCoreReference(root);
  const result = await build({
    entryPoints: [fileURLToPath(new URL('./consumer.mjs', import.meta.url))],
    bundle: true,
    write: false,
    format: 'iife',
    platform: 'browser',
    target: 'es2023',
    nodePaths: [path.join(frontend, 'node_modules')],
    alias: {
      '@zeppelin/lifecycle-core-reference': path.join(root, adapterPath),
      '@zeppelin/notebook-core': path.join(root, corePath, 'public-api.ts')
    },
    tsconfigRaw: { compilerOptions: { experimentalDecorators: true } }
  });
  return { script: result.outputFiles[0].text, provenance };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = process.argv[2];
  if (!root) throw new Error('The pinned Core reference checkout path is required');
  process.stdout.write(JSON.stringify(await buildLifecycleCoreReference(path.resolve(root))));
}
