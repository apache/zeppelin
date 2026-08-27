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

// vite is pinned in package.json: the React remote keeps its own lockfile and drifted to a different minor.
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  // Mirrors the `paths` block in tsconfig.base.json, which Vite does not read.
  // The two library aliases resolve to source, not `dist/`, so a unit run does not wait on a build.
  resolve: {
    alias: [
      // monaco-editor ships no `main` and no `exports` (microsoft/monaco-editor#4848).
      // `module` names editor.main, which boots the full editor and dies in jsdom.
      // 0.55 restores resolution but still points there, so revisit rather than drop.
      {
        find: /^monaco-editor$/,
        replacement: fileURLToPath(new URL('./node_modules/monaco-editor/esm/vs/editor/editor.api.js', import.meta.url))
      },
      { find: /^@zeppelin\/sdk$/, replacement: fileURLToPath(new URL('./projects/zeppelin-sdk/src', import.meta.url)) },
      {
        find: /^@zeppelin\/sdk\/(.*)$/,
        replacement: `${fileURLToPath(new URL('./projects/zeppelin-sdk/src', import.meta.url))}/$1`
      },
      {
        find: /^@zeppelin\/visualization$/,
        replacement: fileURLToPath(new URL('./projects/zeppelin-visualization/src', import.meta.url))
      },
      {
        find: /^@zeppelin\/visualization\/(.*)$/,
        replacement: `${fileURLToPath(new URL('./projects/zeppelin-visualization/src', import.meta.url))}/$1`
      },
      // `@zeppelin/*` falls back to src/environments in tsconfig; Vite aliases do not.
      {
        find: /^@zeppelin\/environment$/,
        replacement: fileURLToPath(new URL('./src/environments/environment.ts', import.meta.url))
      },
      { find: /^@zeppelin\/(.*)$/, replacement: `${fileURLToPath(new URL('./src/app', import.meta.url))}/$1` }
    ]
  },
  // oxc does not apply the decorator options from tsconfig.base.json to specs,
  // which src/tsconfig.json excludes. Undeclared, a decorated spec fails to
  // parse with "Invalid or unexpected token".
  oxc: {
    decorator: {
      emitDecoratorMetadata: true,
      legacy: true
    }
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts', 'projects/zeppelin-sdk/**/*.spec.ts', 'projects/zeppelin-visualization/**/*.spec.ts'],
    setupFiles: ['./test/test-setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: './coverage',
      // No `include`: v4 then reports only the files the specs load. Naming a directory makes the provider parse every source under it, which fails on decorator syntax while ZEPPELIN-6637 is open. Re-measure before widening, since coverage parses separately from the test transform. Note the reported percentage is therefore not whole-tree coverage; see AGENTS.md.
      // TS exclusions are aligned with the e2e coverage reporter's where they overlap. The two measurements stay separate and are not comparable.
      exclude: ['**/*.spec.ts', '**/*.module.ts', '**/*.guard.ts', '**/*.routing.ts', '**/public-api.ts', '**/index.ts']
      // No thresholds on purpose: see zeppelin-web-angular/AGENTS.md.
    }
  }
});
