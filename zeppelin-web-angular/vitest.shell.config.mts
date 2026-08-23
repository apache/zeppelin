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
import { defineConfig } from 'vitest/config';

export default defineConfig({
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
