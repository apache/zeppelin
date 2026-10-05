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
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const reactRemoteRoot = fileURLToPath(new URL('../../projects/zeppelin-react/', import.meta.url));
const requireFromReactRemote = createRequire(join(reactRemoteRoot, 'package.json'));
const webpack = requireFromReactRemote('webpack');
const ModuleFederationPlugin = requireFromReactRemote('webpack/lib/container/ModuleFederationPlugin');
const proofConfig = createRequire(import.meta.url)('./react-remote/webpack.config.js');
const coreEntryPoint = fileURLToPath(
  new URL('../../projects/zeppelin-notebook-core/src/public-api.ts', import.meta.url)
);

test('rejects a React remote that bundles the Shared Notebook Core runtime', async () => {
  const fixtureRoot = mkdtempSync(join(tmpdir(), 'zeppelin-notebook-core-runtime-'));
  try {
    const exposed = join(fixtureRoot, 'BundledCore.ts');
    writeFileSync(exposed, "export * as notebookCore from '@zeppelin/notebook-core';\n");
    const outputPath = join(fixtureRoot, 'dist');
    const compiler = webpack({
      ...proofConfig,
      mode: 'production',
      output: { ...proofConfig.output, path: outputPath },
      plugins: [
        ...proofConfig.plugins.filter(plugin => !(plugin instanceof ModuleFederationPlugin)),
        new ModuleFederationPlugin({
          exposes: { './BundledCore': exposed },
          filename: 'remoteEntry.js',
          name: 'reactApp'
        })
      ]
    });

    const stats = await new Promise((resolveRun, rejectRun) => {
      compiler.run((error, result) => {
        compiler.close(() => (error ? rejectRun(error) : resolveRun(result)));
      });
    });

    const errors = stats.compilation.errors.map(error => error.message);
    assert.ok(
      errors.some(
        message => message.includes('bundled Shared Notebook Core runtime') && message.includes(coreEntryPoint)
      ),
      `expected a Shared Notebook Core rejection naming ${coreEntryPoint}, got: ${errors.join('\n')}`
    );
    assert.equal(existsSync(join(outputPath, 'remoteEntry.js')), false);
  } finally {
    rmSync(fixtureRoot, { recursive: true, force: true });
  }
});
