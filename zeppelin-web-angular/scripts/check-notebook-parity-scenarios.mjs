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

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { loadRegistry, markdownPath, renderMarkdown, validateRegistry, webRoot } from './notebook-parity-scenarios.mjs';

const registry = loadRegistry(webRoot);
const errors = validateRegistry(registry, webRoot, { checkMarkdown: false });
const expectedMarkdown = renderMarkdown(registry);
const actualMarkdownPath = path.join(webRoot, markdownPath);

if (!existsSync(actualMarkdownPath)) {
  errors.push(`${markdownPath} does not exist`);
} else {
  const actualMarkdown = readFileSync(actualMarkdownPath, 'utf8');
  if (actualMarkdown !== expectedMarkdown) {
    errors.push(`${markdownPath} is stale; run npm run generate:notebook-parity-scenarios`);
  }
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exit(1);
}
