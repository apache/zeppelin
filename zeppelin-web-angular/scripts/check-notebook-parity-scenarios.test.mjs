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
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { spawnSync } from 'node:child_process';

import { renderMarkdown, validateRegistry } from './notebook-parity-scenarios.mjs';

function createFixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'notebook-parity-'));
  const webRoot = path.join(root, 'zeppelin-web-angular');
  fs.mkdirSync(path.join(root, 'zeppelin-web-angular/e2e/tests/notebook/main'), { recursive: true });
  fs.mkdirSync(path.join(root, 'zeppelin-web-angular/e2e/scenarios'), { recursive: true });
  fs.mkdirSync(path.join(root, 'e2e/scenarios'), { recursive: true });
  fs.mkdirSync(path.join(root, 'zeppelin-web-angular/src/app/pages/workspace/notebook'), { recursive: true });
  fs.writeFileSync(
    path.join(root, 'zeppelin-web-angular/src/app/pages/workspace/notebook/notebook.component.ts'),
    'class NotebookComponent {}'
  );
  fs.writeFileSync(
    path.join(root, 'zeppelin-web-angular/e2e/tests/notebook/main/notebook-container.spec.ts'),
    "import { test } from '@playwright/test';\ntest('should render', { tag: '@NB-PARITY-001' }, async () => {});"
  );
  spawnSync('git', ['init'], { cwd: root, stdio: 'ignore' });
  spawnSync('git', ['config', 'user.email', 'test@example.invalid'], { cwd: root, stdio: 'ignore' });
  spawnSync('git', ['config', 'user.name', 'Test'], { cwd: root, stdio: 'ignore' });
  spawnSync('git', ['add', '.'], { cwd: root, stdio: 'ignore' });
  spawnSync('git', ['commit', '-m', 'fixture'], { cwd: root, stdio: 'ignore' });
  const commit = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).stdout.trim();
  return { commit, root, webRoot };
}

function writeFixtureSpec(webRoot, source) {
  fs.writeFileSync(path.join(webRoot, 'e2e/tests/notebook/main/notebook-container.spec.ts'), source);
}

function baseRegistry(commit) {
  return {
    $schema: './notebook-parity.schema.json',
    reviewedCommit: commit,
    scenarios: [
      {
        id: 'NB-PARITY-001',
        name: 'Container renders',
        area: 'navigation',
        preconditions: ['note exists'],
        action: 'open the route',
        observableOutcomes: [{ id: 'NB-PARITY-001-OUTCOME-001', description: 'container is visible' }],
        interpreter: null,
        roleExpectations: {
          owner: 'allow',
          writer: 'allow',
          reader: 'allow',
          runner: 'allow'
        },
        roleVerification: {
          owner: 'unverified',
          writer: 'unverified',
          reader: 'unverified',
          runner: 'unverified'
        },
        implementationEvidence: [
          {
            path: 'zeppelin-web-angular/src/app/pages/workspace/notebook/notebook.component.ts',
            symbol: 'NotebookComponent'
          }
        ],
        verificationEvidence: [],
        coverage: {
          status: 'covered',
          tests: [
            {
              path: 'zeppelin-web-angular/e2e/tests/notebook/main/notebook-container.spec.ts',
              tag: '@NB-PARITY-001'
            }
          ],
          issues: [],
          uncoveredOutcomes: []
        }
      }
    ]
  };
}

test('validates a current registry and generated markdown', () => {
  const { commit, root, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  fs.writeFileSync(path.join(webRoot, 'e2e/scenarios/notebook-parity.md'), renderMarkdown(registry));

  assert.deepEqual(validateRegistry(registry, webRoot), []);
});

test('accepts multiline executable Playwright test declarations', () => {
  const { commit, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  writeFixtureSpec(
    webRoot,
    `import { test } from '@playwright/test';
    test(
      'should render',
      { tag: '@NB-PARITY-001' },
      async () => {}
    );`
  );
  fs.writeFileSync(path.join(webRoot, 'e2e/scenarios/notebook-parity.md'), renderMarkdown(registry));

  assert.deepEqual(validateRegistry(registry, webRoot), []);
});

test('rejects duplicate ids and stale markdown', () => {
  const { commit, root, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  registry.scenarios.push(structuredClone(registry.scenarios[0]));
  fs.writeFileSync(path.join(webRoot, 'e2e/scenarios/notebook-parity.md'), 'stale\n');

  const errors = validateRegistry(registry, webRoot).join('\n');
  assert.match(errors, /is duplicated/);
});

test('rejects false covered claims without matching Playwright tag', () => {
  const { commit, root, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  registry.scenarios[0].coverage.tests[0].tag = '@NB-PARITY-999';
  fs.writeFileSync(path.join(webRoot, 'e2e/scenarios/notebook-parity.md'), renderMarkdown(registry));

  const errors = validateRegistry(registry, webRoot).join('\n');
  assert.match(errors, /tag must be @NB-PARITY-001/);
});

test('rejects commented-only, helper-string, and skipped Playwright coverage claims', () => {
  const cases = [
    {
      name: 'commented-only',
      source: "// test('should render', { tag: '@NB-PARITY-001' }, async () => {});"
    },
    {
      name: 'helper-string',
      source: "const parityTag = '@NB-PARITY-001';\ntest('should render', { tag: parityTag }, async () => {});"
    },
    {
      name: 'test.skip',
      source:
        "import { test } from '@playwright/test';\ntest.skip('should render', { tag: '@NB-PARITY-001' }, async () => {});"
    },
    {
      name: 'skipped describe',
      source:
        "import { test } from '@playwright/test';\ntest.describe.skip('disabled', () => { test('should render', { tag: '@NB-PARITY-001' }, async () => {}); });"
    },
    {
      name: 'non-Playwright test helper',
      source: "const test = () => undefined;\ntest('should render', { tag: '@NB-PARITY-001' }, async () => {});"
    }
  ];

  for (const { name, source } of cases) {
    const { commit, webRoot } = createFixture();
    const registry = baseRegistry(commit);
    writeFixtureSpec(webRoot, source);
    fs.writeFileSync(path.join(webRoot, 'e2e/scenarios/notebook-parity.md'), renderMarkdown(registry));

    const errors = validateRegistry(registry, webRoot).join('\n');
    assert.match(errors, /tag is not declared by an executable test\(\)/, name);
  }
});

test('requires Jira issues for gaps', () => {
  const { commit, root, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  registry.scenarios[0].coverage.status = 'gap';
  registry.scenarios[0].coverage.tests = [];
  fs.writeFileSync(path.join(webRoot, 'e2e/scenarios/notebook-parity.md'), renderMarkdown(registry));

  const errors = validateRegistry(registry, webRoot).join('\n');
  assert.match(errors, /coverage\.issues must NOT have fewer than 1 items/);
});

test('requires executable coverage, a Jira issue, and named uncovered outcomes for partial scenarios', () => {
  const { commit, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  registry.scenarios[0].coverage.status = 'partial';
  registry.scenarios[0].coverage.issues = [];

  const errors = validateRegistry(registry, webRoot).join('\n');
  assert.match(errors, /coverage\.issues must NOT have fewer than 1 items/);

  registry.scenarios[0].coverage.issues = ['ZEPPELIN-1234'];
  registry.scenarios[0].coverage.tests = [];
  const missingTestErrors = validateRegistry(registry, webRoot).join('\n');
  assert.match(missingTestErrors, /coverage\.tests must NOT have fewer than 1 items/);
  assert.match(missingTestErrors, /coverage\.uncoveredOutcomes must NOT have fewer than 1 items/);
});

test('rejects invalid Jira issue keys and extra role expectation fields', () => {
  const { commit, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  registry.scenarios[0].coverage.issues = ['OTHER-123'];
  registry.scenarios[0].roleExpectations.admin = 'allow';

  const errors = validateRegistry(registry, webRoot).join('\n');
  assert.match(errors, /must match pattern "\^ZEPPELIN-\[0-9\]\+\$"/);
  assert.match(errors, /roleExpectations must NOT have additional properties/);
});

test('rejects malformed coverage arrays without applying unsafe coverage rules', () => {
  const { commit, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  registry.scenarios[0].coverage.tests = null;
  registry.scenarios[0].coverage.issues = null;
  registry.scenarios[0].coverage.uncoveredOutcomes = null;

  assert.doesNotThrow(() => validateRegistry(registry, webRoot));
  const errors = validateRegistry(registry, webRoot).join('\n');
  assert.match(errors, /coverage\.tests must be array/);
  assert.match(errors, /coverage\.issues must be array/);
  assert.match(errors, /coverage\.uncoveredOutcomes must be array/);
});

test('rejects malformed test entries without throwing', () => {
  const { commit, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  registry.scenarios[0].coverage.tests = [null];

  assert.doesNotThrow(() => validateRegistry(registry, webRoot));
  const errors = validateRegistry(registry, webRoot).join('\n');
  assert.match(errors, /coverage\.tests\.0 must be object/);

  registry.scenarios[0].coverage.tests = [
    {
      path: 'zeppelin-web-angular/e2e/tests/notebook/main/notebook-container.spec.ts',
      tag: '@NB-PARITY-001',
      unexpected: true
    }
  ];
  const metadataErrors = validateRegistry(registry, webRoot).join('\n');
  assert.match(metadataErrors, /must NOT have additional properties/);
});

test('allows verified role evidence and optional role metadata', () => {
  const { commit, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  registry.scenarios[0].roleVerification.reader = 'verified';
  assert.deepEqual(validateRegistry(registry, webRoot, { checkMarkdown: false }), []);

  delete registry.scenarios[0].roleExpectations;
  delete registry.scenarios[0].roleVerification;
  assert.deepEqual(validateRegistry(registry, webRoot, { checkMarkdown: false }), []);
});

test('rejects implementation evidence paths outside the repository', () => {
  const { commit, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  registry.scenarios[0].implementationEvidence[0].path = '../outside-repository.ts';

  const errors = validateRegistry(registry, webRoot).join('\n');
  assert.match(errors, /implementationEvidence\[0\].path does not exist/);
});

test('references uncovered outcomes by stable id', () => {
  const { commit, webRoot } = createFixture();
  const registry = baseRegistry(commit);
  registry.scenarios[0].coverage.status = 'partial';
  registry.scenarios[0].coverage.issues = ['ZEPPELIN-1234'];
  registry.scenarios[0].coverage.uncoveredOutcomes = ['NB-PARITY-001-OUTCOME-999'];

  const errors = validateRegistry(registry, webRoot, { checkMarkdown: false }).join('\n');
  assert.match(errors, /must reference an observable outcome id/);
});

test('renders generated Markdown with exactly one trailing newline', () => {
  const { commit } = createFixture();
  const markdown = renderMarkdown(baseRegistry(commit));

  assert.equal(markdown.endsWith('\n'), true);
  assert.equal(markdown.endsWith('\n\n'), false);
});

test('renders generated Markdown with an Apache License header', () => {
  const { commit } = createFixture();
  const markdown = renderMarkdown(baseRegistry(commit));

  assert.match(markdown, /^<!--\n  Licensed under the Apache License, Version 2\.0/);
});

test('renders baseline commit and coverage evidence caveats', () => {
  const { commit } = createFixture();
  const markdown = renderMarkdown(baseRegistry(commit));

  assert.match(markdown, new RegExp(`Scenario/Angular baseline commit: \`${commit}\``));
  assert.match(
    markdown,
    /`covered` mechanically means this registry points to a matching executable Playwright test tag/
  );
  assert.match(markdown, /Semantic adequacy and runtime pass\/fail remain review and CI evidence/);
});
