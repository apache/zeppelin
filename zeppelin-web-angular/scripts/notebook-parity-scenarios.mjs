#!/usr/bin/env node
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

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import Ajv from 'ajv';
import ts from 'typescript';

export const registryPath = 'e2e/scenarios/notebook-parity.json';
export const schemaPath = 'e2e/scenarios/notebook-parity.schema.json';
export const markdownPath = 'e2e/scenarios/notebook-parity.md';
export const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const roles = ['owner', 'writer', 'reader', 'runner'];

const escapeTableCell = value => String(value).replace(/\|/g, '\\|').replace(/\n/g, '<br>');
const readJson = file => JSON.parse(readFileSync(file, 'utf8'));
const registrySchema = readJson(path.join(webRoot, schemaPath));
const validateSchema = new Ajv({ allErrors: true, allowUnionTypes: true }).compile(registrySchema);

const resolveRepositoryPath = (root, relativePath) => {
  if (typeof relativePath !== 'string' || relativePath.length === 0 || path.isAbsolute(relativePath)) {
    return null;
  }

  const repositoryRoot = path.resolve(root, '..');
  const isWithinRepository = candidate => candidate.startsWith(`${repositoryRoot}${path.sep}`);
  const candidates = [path.resolve(root, relativePath), path.resolve(repositoryRoot, relativePath)];
  return (
    candidates.find(candidate => isWithinRepository(candidate) && existsSync(candidate)) ??
    candidates.find(isWithinRepository) ??
    null
  );
};

const getPlaywrightTestBindings = sourceFile => {
  const bindings = new Set();
  for (const node of sourceFile.statements) {
    if (
      ts.isImportDeclaration(node) &&
      ts.isStringLiteral(node.moduleSpecifier) &&
      node.moduleSpecifier.text === '@playwright/test' &&
      node.importClause?.namedBindings &&
      ts.isNamedImports(node.importClause.namedBindings)
    ) {
      for (const element of node.importClause.namedBindings.elements) {
        if ((element.propertyName ?? element.name).text === 'test') {
          bindings.add(element.name.text);
        }
      }
    }
  }
  return bindings;
};

const isSkippedDescribeCall = (node, testBindings) =>
  ts.isCallExpression(node) &&
  ts.isPropertyAccessExpression(node.expression) &&
  node.expression.name.text === 'skip' &&
  ts.isPropertyAccessExpression(node.expression.expression) &&
  node.expression.expression.name.text === 'describe' &&
  ts.isIdentifier(node.expression.expression.expression) &&
  testBindings.has(node.expression.expression.expression.text);

const isInsideSkippedDescribe = (node, testBindings) => {
  for (let current = node.parent; current; current = current.parent) {
    if (isSkippedDescribeCall(current, testBindings)) {
      return true;
    }
  }
  return false;
};

const readStaticTags = options => {
  if (!options || !ts.isObjectLiteralExpression(options)) {
    return [];
  }
  const tagProperty = options.properties.find(
    property =>
      ts.isPropertyAssignment(property) &&
      ((ts.isIdentifier(property.name) && property.name.text === 'tag') ||
        (ts.isStringLiteral(property.name) && property.name.text === 'tag'))
  );
  if (!tagProperty || !ts.isPropertyAssignment(tagProperty)) {
    return [];
  }
  if (ts.isStringLiteralLike(tagProperty.initializer)) {
    return [tagProperty.initializer.text];
  }
  if (ts.isArrayLiteralExpression(tagProperty.initializer)) {
    return tagProperty.initializer.elements.filter(ts.isStringLiteralLike).map(element => element.text);
  }
  return [];
};

const getExecutablePlaywrightTestTags = source => {
  const sourceFile = ts.createSourceFile('spec.ts', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const testBindings = getPlaywrightTestBindings(sourceFile);
  if (testBindings.size === 0) {
    return new Set();
  }

  const tags = new Set();
  const visit = node => {
    if (
      ts.isCallExpression(node) &&
      ts.isIdentifier(node.expression) &&
      testBindings.has(node.expression.text) &&
      !isInsideSkippedDescribe(node, testBindings)
    ) {
      for (const tag of readStaticTags(node.arguments[1])) {
        tags.add(tag);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return tags;
};

const formatSchemaError = error => {
  const location = error.instancePath ? error.instancePath.slice(1).replaceAll('/', '.') : 'registry';
  switch (error.keyword) {
    case 'additionalProperties':
      return `${location}.${error.params.additionalProperty} is not allowed`;
    case 'minItems':
      return `${location} must contain at least ${error.params.limit} item(s)`;
    case 'maxItems':
      return `${location} must contain at most ${error.params.limit} item(s)`;
    case 'pattern':
      return `${location} must match ${error.params.pattern}`;
    case 'required':
      return `${location}.${error.params.missingProperty} is required`;
    case 'type':
      return `${location} must be ${error.params.type}`;
    default:
      return `${location} ${error.message}`;
  }
};

const testDeclaresExecutableTag = (root, test) => {
  const absolutePath = resolveRepositoryPath(root, test.path);
  if (!absolutePath || !existsSync(absolutePath)) {
    return false;
  }
  return getExecutablePlaywrightTestTags(readFileSync(absolutePath, 'utf8')).has(test.tag);
};

export const renderMarkdown = registry => {
  const lines = [
    '<!--',
    '  Licensed under the Apache License, Version 2.0 (the "License");',
    '  you may not use this file except in compliance with the License.',
    '  You may obtain a copy of the License at',
    '      http://www.apache.org/licenses/LICENSE-2.0',
    '  Unless required by applicable law or agreed to in writing, software',
    '  distributed under the License is distributed on an "AS IS" BASIS,',
    '  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.',
    '  See the License for the specific language governing permissions and',
    '  limitations under the License.',
    '-->',
    '',
    '# Notebook Parity Scenarios',
    '',
    '<!-- Generated by scripts/generate-notebook-parity-scenarios.mjs. Do not edit directly. -->',
    '',
    'Schema: [notebook-parity.schema.json](./notebook-parity.schema.json)',
    '',
    `Scenario/Angular baseline commit: \`${registry.reviewedCommit}\``,
    '',
    'Scope note: This is a prioritized baseline, not a complete Notebook inventory. Before a React vertical slice is declared ready, add every affected behavior to this registry and classify its evidence.',
    '',
    'Coverage note: `covered` mechanically means this registry points to a matching executable Playwright test tag. Semantic adequacy and runtime pass/fail remain review and CI evidence. Role expectations are recorded only when the outcome varies by role, and role verification records whether that expectation has been tested.',
    '',
    '| ID | Area | Scenario | Coverage | Roles | Tests | Issues |',
    '| --- | --- | --- | --- | --- | --- | --- |'
  ];

  for (const scenario of registry.scenarios) {
    const rolesText = scenario.roleExpectations
      ? roles.map(role => `${role}: ${scenario.roleExpectations[role]}`).join('<br>')
      : 'not-applicable';
    const testsText =
      scenario.coverage.tests.length === 0
        ? ''
        : scenario.coverage.tests.map(test => `${test.path}<br>${test.tag}`).join('<br><br>');
    const issuesText = scenario.coverage.issues.join(', ');
    lines.push(
      `| ${scenario.id} | ${scenario.area} | ${escapeTableCell(scenario.name)} | ${scenario.coverage.status} | ${escapeTableCell(rolesText)} | ${escapeTableCell(testsText)} | ${issuesText} |`
    );
  }

  lines.push('', '## Scenario Details', '');

  for (const scenario of registry.scenarios) {
    lines.push(`### ${scenario.id} ${scenario.name}`);
    lines.push('');
    lines.push(`- Area: ${scenario.area}`);
    lines.push(`- Coverage: ${scenario.coverage.status}`);
    lines.push(`- Interpreter: ${scenario.interpreter ?? 'not-applicable'}`);
    lines.push(
      `- Role verification: ${scenario.roleVerification ? roles.map(role => `${role}: ${scenario.roleVerification[role]}`).join('; ') : 'not-applicable'}`
    );
    lines.push(`- Preconditions: ${scenario.preconditions.join(' ')}`);
    lines.push(`- Action: ${scenario.action}`);
    lines.push(
      `- Observable outcomes: ${scenario.observableOutcomes.map(outcome => `${outcome.id}: ${outcome.description}`).join(' ')}`
    );
    lines.push(
      `- Implementation evidence: ${scenario.implementationEvidence.map(item => `${item.path} (${item.symbol})`).join('; ') || 'not-applicable'}`
    );
    lines.push(
      `- Verification evidence: ${scenario.verificationEvidence.map(item => `${item.path} (${item.symbol})`).join('; ') || 'not-applicable'}`
    );
    if (scenario.coverage.uncoveredOutcomes.length > 0) {
      const outcomesById = new Map(scenario.observableOutcomes.map(outcome => [outcome.id, outcome.description]));
      lines.push(
        `- Uncovered outcomes: ${scenario.coverage.uncoveredOutcomes.map(id => `${id}: ${outcomesById.get(id)}`).join(' ')}`
      );
    }
    lines.push('');
  }

  return `${lines.join('\n').replace(/\n+$/, '')}\n`;
};

export const validateRegistry = (registry, root = webRoot, { checkMarkdown = true } = {}) => {
  const errors = [];

  if (!validateSchema(registry)) {
    errors.push(...validateSchema.errors.map(formatSchemaError));
  }

  if (!registry || typeof registry !== 'object' || Array.isArray(registry)) {
    return errors;
  }

  if (typeof registry.reviewedCommit === 'string' && /^[0-9a-f]{40}$/.test(registry.reviewedCommit)) {
    try {
      execFileSync('git', ['cat-file', '-e', `${registry.reviewedCommit}^{commit}`], { cwd: root, stdio: 'ignore' });
    } catch {
      errors.push(`reviewedCommit is not available in this checkout: ${registry.reviewedCommit}`);
    }
  }
  if (!Array.isArray(registry.scenarios) || registry.scenarios.length === 0) {
    return errors;
  }

  const ids = new Set();
  let previousId = '';
  for (const [index, scenario] of registry.scenarios.entries()) {
    const prefix = `scenarios[${index}]`;
    if (!scenario || typeof scenario !== 'object') {
      continue;
    }
    if (typeof scenario.id === 'string' && ids.has(scenario.id)) {
      errors.push(`${prefix}.id is duplicated: ${scenario.id}`);
    } else if (typeof scenario.id === 'string') {
      if (previousId && scenario.id <= previousId) {
        errors.push(`${prefix}.id must sort after ${previousId}`);
      }
      previousId = scenario.id;
      ids.add(scenario.id);
    }

    if (scenario.roleExpectations && scenario.roleVerification) {
      for (const role of roles) {
        if (
          scenario.roleExpectations[role] === 'not-applicable' &&
          scenario.roleVerification[role] !== 'not-applicable'
        ) {
          errors.push(`${prefix}.roleVerification.${role} must be not-applicable`);
        } else if (
          scenario.roleExpectations[role] !== 'not-applicable' &&
          scenario.roleVerification[role] === 'not-applicable'
        ) {
          errors.push(
            `${prefix}.roleVerification.${role} must not be not-applicable when roleExpectations.${role} is ${scenario.roleExpectations[role]}`
          );
        }
      }
    }

    for (const evidenceField of ['implementationEvidence', 'verificationEvidence']) {
      if (Array.isArray(scenario[evidenceField])) {
        for (const [evidenceIndex, evidence] of scenario[evidenceField].entries()) {
          if (!evidence || typeof evidence.path !== 'string') {
            continue;
          }
          const evidencePath = resolveRepositoryPath(root, evidence.path);
          if (!evidencePath || !existsSync(evidencePath)) {
            errors.push(`${prefix}.${evidenceField}[${evidenceIndex}].path does not exist: ${evidence.path}`);
          }
        }
      }
    }

    const outcomeIds = new Set();
    if (Array.isArray(scenario.observableOutcomes)) {
      for (const [outcomeIndex, outcome] of scenario.observableOutcomes.entries()) {
        if (!outcome || typeof outcome.id !== 'string') {
          continue;
        }
        if (!outcome.id.startsWith(`${scenario.id}-OUTCOME-`)) {
          errors.push(`${prefix}.observableOutcomes[${outcomeIndex}].id must start with ${scenario.id}-OUTCOME-`);
        } else if (outcomeIds.has(outcome.id)) {
          errors.push(`${prefix}.observableOutcomes[${outcomeIndex}].id is duplicated: ${outcome.id}`);
        }
        outcomeIds.add(outcome.id);
      }
    }

    const coverage = scenario.coverage;
    if (!coverage || typeof coverage !== 'object') {
      continue;
    }
    const uncoveredOutcomes = Array.isArray(coverage.uncoveredOutcomes) ? coverage.uncoveredOutcomes : [];
    for (const [outcomeIndex, outcomeId] of uncoveredOutcomes.entries()) {
      if (!outcomeIds.has(outcomeId)) {
        errors.push(`${prefix}.coverage.uncoveredOutcomes[${outcomeIndex}] must reference an observable outcome id`);
      }
    }
    const coverageTests = Array.isArray(coverage.tests) ? coverage.tests : [];
    for (const [testIndex, test] of coverageTests.entries()) {
      if (!test || typeof test !== 'object') {
        continue;
      }
      if (typeof test.path === 'string') {
        const testPath = resolveRepositoryPath(root, test.path);
        if (!testPath || !existsSync(testPath)) {
          errors.push(`${prefix}.coverage.tests[${testIndex}].path does not exist: ${test.path}`);
        }
        if (coverage.status === 'covered' && !test.path.startsWith('zeppelin-web-angular/e2e/tests/notebook/')) {
          errors.push(`${prefix}.coverage.tests[${testIndex}].path must be in the notebook E2E suite`);
        }
      }
      if (test.tag !== `@${scenario.id}`) {
        errors.push(`${prefix}.coverage.tests[${testIndex}].tag must be @${scenario.id}`);
      } else if (typeof test.path === 'string' && !testDeclaresExecutableTag(root, test)) {
        errors.push(
          `${prefix}.coverage.tests[${testIndex}].tag is not declared by an executable test() in ${test.path}`
        );
      }
    }
  }

  if (checkMarkdown && errors.length === 0) {
    const actualMarkdownPath = path.join(root, markdownPath);
    if (!existsSync(actualMarkdownPath)) {
      errors.push(`${markdownPath} does not exist`);
    } else if (readFileSync(actualMarkdownPath, 'utf8') !== renderMarkdown(registry)) {
      errors.push(`${markdownPath} is stale; run npm run generate:notebook-parity-scenarios`);
    }
  }

  return errors;
};

export const loadRegistry = (root = webRoot) => readJson(path.join(root, registryPath));
