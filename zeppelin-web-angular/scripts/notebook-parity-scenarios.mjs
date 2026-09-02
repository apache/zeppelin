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

export const registryPath = 'e2e/scenarios/notebook-parity.json';
export const markdownPath = 'e2e/scenarios/notebook-parity.md';
export const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const allowedAreas = new Set([
  'editor',
  'execution',
  'result',
  'visualization',
  'shortcut',
  'permission',
  'collaboration',
  'navigation',
  'persistence',
  'lifecycle',
  'theme',
  'accessibility'
]);
const allowedCoverageStatuses = new Set(['covered', 'partial', 'gap', 'blocked']);
const allowedRoleExpectations = new Set(['allow', 'deny', 'not-applicable']);
const allowedRoleVerificationStatuses = new Set(['unverified', 'not-applicable']);
const allowedProjects = new Set(['chromium', 'firefox', 'webkit']);
const jiraIssuePattern = /^ZEPPELIN-\d+$/;
const roles = ['owner', 'writer', 'reader', 'runner'];

const escapeTableCell = value => String(value).replace(/\|/g, '\\|').replace(/\n/g, '<br>');
const readJson = file => JSON.parse(readFileSync(file, 'utf8'));

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

const assertArrayOfStrings = (errors, value, field) => {
  if (
    !Array.isArray(value) ||
    value.length === 0 ||
    value.some(item => typeof item !== 'string' || item.length === 0)
  ) {
    errors.push(`${field} must be a non-empty string array`);
  }
};

const skipWhitespaceAndComments = (source, start) => {
  let index = start;
  while (index < source.length) {
    if (/\s/.test(source[index])) {
      index += 1;
    } else if (source[index] === '/' && source[index + 1] === '/') {
      index = source.indexOf('\n', index + 2);
      if (index === -1) {
        return source.length;
      }
    } else if (source[index] === '/' && source[index + 1] === '*') {
      index = source.indexOf('*/', index + 2);
      if (index === -1) {
        return source.length;
      }
      index += 2;
    } else {
      return index;
    }
  }
  return index;
};

const readStringLiteral = (source, start) => {
  const quote = source[start];
  if (quote !== "'" && quote !== '"' && quote !== '`') {
    return null;
  }

  let value = '';
  for (let index = start + 1; index < source.length; index += 1) {
    const character = source[index];
    if (character === '\\') {
      value += source[index + 1] ?? '';
      index += 1;
    } else if (quote === '`' && character === '$' && source[index + 1] === '{') {
      return null;
    } else if (character === quote) {
      return value;
    } else {
      value += character;
    }
  }
  return null;
};

const skipStringLiteral = (source, start) => {
  const quote = source[start];
  for (let index = start + 1; index < source.length; index += 1) {
    const character = source[index];
    if (character === '\\') {
      index += 1;
    } else if (character === quote) {
      return index + 1;
    }
  }
  return source.length;
};

const isIdentifierCharacter = character => /[A-Za-z0-9_$]/.test(character ?? '');

const previousNonWhitespaceCharacter = (source, start) => {
  for (let index = start - 1; index >= 0; index -= 1) {
    if (!/\s/.test(source[index])) {
      return source[index];
    }
  }
  return '';
};

const importsPlaywrightTest = source =>
  /import\s+(?:[\s\S]*?\btest\b[\s\S]*?)\s+from\s+['"]@playwright\/test['"]/.test(source);

const findSkippedDescribeBlocks = source => {
  const blocks = [];
  const marker = 'test.describe.skip';
  for (let start = source.indexOf(marker); start !== -1; start = source.indexOf(marker, start + marker.length)) {
    const bodyStart = source.indexOf('{', start + marker.length);
    if (bodyStart === -1) {
      continue;
    }
    let depth = 0;
    for (let index = bodyStart; index < source.length; index += 1) {
      if (source[index] === "'" || source[index] === '"' || source[index] === '`') {
        index = skipStringLiteral(source, index) - 1;
      } else if (source[index] === '/' && source[index + 1] === '/') {
        index = source.indexOf('\n', index + 2);
        if (index === -1) {
          break;
        }
      } else if (source[index] === '/' && source[index + 1] === '*') {
        index = source.indexOf('*/', index + 2);
        if (index === -1) {
          break;
        }
        index += 1;
      } else if (source[index] === '{') {
        depth += 1;
      } else if (source[index] === '}' && --depth === 0) {
        blocks.push([start, index]);
        break;
      }
    }
  }
  return blocks;
};

const getExecutablePlaywrightTestTitles = source => {
  if (!importsPlaywrightTest(source)) {
    return new Set();
  }

  const titles = new Set();
  const skippedDescribeBlocks = findSkippedDescribeBlocks(source);
  for (let index = 0; index < source.length; index += 1) {
    if (source[index] === '/' && source[index + 1] === '/') {
      index = source.indexOf('\n', index + 2);
      if (index === -1) {
        break;
      }
    } else if (source[index] === '/' && source[index + 1] === '*') {
      index = source.indexOf('*/', index + 2);
      if (index === -1) {
        break;
      }
      index += 1;
    } else if (source[index] === "'" || source[index] === '"' || source[index] === '`') {
      index = skipStringLiteral(source, index) - 1;
    } else if (
      source.startsWith('test', index) &&
      !isIdentifierCharacter(source[index - 1]) &&
      !isIdentifierCharacter(source[index + 4]) &&
      previousNonWhitespaceCharacter(source, index) !== '.'
    ) {
      const openParen = skipWhitespaceAndComments(source, index + 4);
      if (source[openParen] !== '(') {
        continue;
      }
      const titleStart = skipWhitespaceAndComments(source, openParen + 1);
      const title = readStringLiteral(source, titleStart);
      if (title !== null && !skippedDescribeBlocks.some(([start, end]) => index >= start && index <= end)) {
        titles.add(title);
      }
    }
  }

  return titles;
};

const testDeclaresExecutableTitle = (root, test) => {
  const absolutePath = resolveRepositoryPath(root, test.path);
  if (!absolutePath || !existsSync(absolutePath)) {
    return false;
  }
  return getExecutablePlaywrightTestTitles(readFileSync(absolutePath, 'utf8')).has(test.title);
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
    `Schema version: ${registry.schemaVersion}`,
    '',
    `Scenario/Angular baseline commit: \`${registry.reviewedCommit}\``,
    '',
    'Scope note: This is a prioritized baseline, not a complete Notebook inventory. Before a React vertical slice is declared ready, add every affected behavior to this registry and classify its evidence.',
    '',
    'Coverage note: `covered` mechanically means this registry points to a matching executable Playwright test declaration. Semantic adequacy and runtime pass/fail remain review and CI evidence. Role expectations and role verification are deliberately separate.',
    '',
    '| ID | Area | Scenario | Coverage | Roles | Tests | Issues |',
    '| --- | --- | --- | --- | --- | --- | --- |'
  ];

  for (const scenario of registry.scenarios) {
    const rolesText = roles.map(role => `${role}: ${scenario.roleExpectations[role]}`).join('<br>');
    const testsText =
      scenario.coverage.tests.length === 0
        ? ''
        : scenario.coverage.tests.map(test => `${test.path}<br>${test.title}`).join('<br><br>');
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
    lines.push(`- Role verification: ${roles.map(role => `${role}: ${scenario.roleVerification[role]}`).join('; ')}`);
    lines.push(`- Preconditions: ${scenario.preconditions.join(' ')}`);
    lines.push(`- Action: ${scenario.action}`);
    lines.push(`- Observable outcomes: ${scenario.observableOutcomes.join(' ')}`);
    lines.push(`- Evidence: ${scenario.evidence.map(item => `${item.path} (${item.symbol})`).join('; ')}`);
    if (scenario.coverage.tests.length > 0) {
      lines.push(
        `- Browser projects: ${scenario.coverage.tests
          .map(test => `${test.title}: ${test.projects.join(', ')}`)
          .join('; ')}`
      );
    }
    if (scenario.coverage.uncoveredOutcomes.length > 0) {
      lines.push(`- Uncovered outcomes: ${scenario.coverage.uncoveredOutcomes.join(' ')}`);
    }
    lines.push('');
  }

  return `${lines.join('\n').replace(/\n+$/, '')}\n`;
};

export const validateRegistry = (registry, root = webRoot, { checkMarkdown = true } = {}) => {
  const errors = [];

  if (registry.schemaVersion !== 1) {
    errors.push('schemaVersion must be 1');
  }
  if (typeof registry.reviewedCommit !== 'string' || !/^[0-9a-f]{40}$/.test(registry.reviewedCommit)) {
    errors.push('reviewedCommit must be a full 40-character commit hash');
  } else {
    try {
      execFileSync('git', ['cat-file', '-e', `${registry.reviewedCommit}^{commit}`], { cwd: root, stdio: 'ignore' });
    } catch {
      errors.push(`reviewedCommit is not available in this checkout: ${registry.reviewedCommit}`);
    }
  }
  if (!Array.isArray(registry.scenarios) || registry.scenarios.length === 0) {
    errors.push('scenarios must be a non-empty array');
    return errors;
  }

  const ids = new Set();
  let previousId = '';
  for (const [index, scenario] of registry.scenarios.entries()) {
    const prefix = `scenarios[${index}]`;
    if (typeof scenario.id !== 'string' || !/^NB-PARITY-\d{3}$/.test(scenario.id)) {
      errors.push(`${prefix}.id must match NB-PARITY-###`);
    } else if (ids.has(scenario.id)) {
      errors.push(`${prefix}.id is duplicated: ${scenario.id}`);
    } else {
      if (previousId && scenario.id <= previousId) {
        errors.push(`${prefix}.id must sort after ${previousId}`);
      }
      previousId = scenario.id;
      ids.add(scenario.id);
    }

    if (typeof scenario.name !== 'string' || scenario.name.length === 0) {
      errors.push(`${prefix}.name is required`);
    }
    if (!allowedAreas.has(scenario.area)) {
      errors.push(`${prefix}.area is invalid: ${scenario.area}`);
    }
    assertArrayOfStrings(errors, scenario.preconditions, `${prefix}.preconditions`);
    if (typeof scenario.action !== 'string' || scenario.action.length === 0) {
      errors.push(`${prefix}.action is required`);
    }
    assertArrayOfStrings(errors, scenario.observableOutcomes, `${prefix}.observableOutcomes`);
    if (!(typeof scenario.interpreter === 'string' || scenario.interpreter === null)) {
      errors.push(`${prefix}.interpreter must be a string or null`);
    }
    for (const role of roles) {
      if (!allowedRoleExpectations.has(scenario.roleExpectations?.[role])) {
        errors.push(`${prefix}.roleExpectations.${role} is invalid`);
      }
    }
    const roleKeys = Object.keys(scenario.roleExpectations ?? {}).sort();
    if (roleKeys.join(',') !== [...roles].sort().join(',')) {
      errors.push(`${prefix}.roleExpectations must contain exactly ${roles.join(', ')}`);
    }
    for (const role of roles) {
      if (!allowedRoleVerificationStatuses.has(scenario.roleVerification?.[role])) {
        errors.push(`${prefix}.roleVerification.${role} is invalid`);
      }
      if (
        scenario.roleExpectations?.[role] === 'not-applicable' &&
        scenario.roleVerification?.[role] !== 'not-applicable'
      ) {
        errors.push(`${prefix}.roleVerification.${role} must be not-applicable`);
      }
    }
    const roleVerificationKeys = Object.keys(scenario.roleVerification ?? {}).sort();
    if (roleVerificationKeys.join(',') !== [...roles].sort().join(',')) {
      errors.push(`${prefix}.roleVerification must contain exactly ${roles.join(', ')}`);
    }

    if (!Array.isArray(scenario.evidence) || scenario.evidence.length === 0) {
      errors.push(`${prefix}.evidence must be a non-empty array`);
    } else {
      for (const [evidenceIndex, evidence] of scenario.evidence.entries()) {
        const evidencePath = resolveRepositoryPath(root, evidence.path);
        if (!evidencePath || !existsSync(evidencePath)) {
          errors.push(`${prefix}.evidence[${evidenceIndex}].path does not exist: ${evidence.path}`);
        }
        if (typeof evidence.symbol !== 'string' || evidence.symbol.length === 0) {
          errors.push(`${prefix}.evidence[${evidenceIndex}].symbol is required`);
        }
      }
    }

    const coverage = scenario.coverage;
    if (!allowedCoverageStatuses.has(coverage?.status)) {
      errors.push(`${prefix}.coverage.status is invalid`);
      continue;
    }
    if (!Array.isArray(coverage.tests)) {
      errors.push(`${prefix}.coverage.tests must be an array`);
    }
    if (!Array.isArray(coverage.issues)) {
      errors.push(`${prefix}.coverage.issues must be an array`);
    }
    if (!Array.isArray(coverage.uncoveredOutcomes)) {
      errors.push(`${prefix}.coverage.uncoveredOutcomes must be an array`);
    }
    const coverageTests = Array.isArray(coverage.tests) ? coverage.tests : [];
    const coverageIssues = Array.isArray(coverage.issues) ? coverage.issues : [];
    const uncoveredOutcomes = Array.isArray(coverage.uncoveredOutcomes) ? coverage.uncoveredOutcomes : [];
    if (coverage.status === 'covered' && coverageTests.length === 0) {
      errors.push(`${prefix}.coverage.tests is required for covered scenarios`);
    }
    if (coverage.status === 'partial' && coverageTests.length === 0) {
      errors.push(`${prefix}.coverage.tests is required for partial scenarios`);
    }
    if (coverage.status === 'partial' && coverageIssues.length === 0) {
      errors.push(`${prefix}.coverage.issues is required for partial scenarios`);
    }
    if (coverage.status === 'partial' && uncoveredOutcomes.length === 0) {
      errors.push(`${prefix}.coverage.uncoveredOutcomes is required for partial scenarios`);
    }
    if (coverage.status !== 'partial' && uncoveredOutcomes.length > 0) {
      errors.push(`${prefix}.coverage.uncoveredOutcomes is only valid for partial scenarios`);
    }
    for (const [outcomeIndex, outcome] of uncoveredOutcomes.entries()) {
      if (typeof outcome !== 'string' || !scenario.observableOutcomes.includes(outcome)) {
        errors.push(`${prefix}.coverage.uncoveredOutcomes[${outcomeIndex}] must reference an observable outcome`);
      }
    }
    if ((coverage.status === 'gap' || coverage.status === 'blocked') && coverageIssues.length === 0) {
      errors.push(`${prefix}.coverage.issues is required for ${coverage.status} scenarios`);
    }
    for (const [issueIndex, issue] of coverageIssues.entries()) {
      if (typeof issue !== 'string' || !jiraIssuePattern.test(issue)) {
        errors.push(`${prefix}.coverage.issues[${issueIndex}] must match ZEPPELIN-####`);
      }
    }
    for (const [testIndex, test] of coverageTests.entries()) {
      if (!test || typeof test !== 'object') {
        errors.push(`${prefix}.coverage.tests[${testIndex}] must be an object`);
        continue;
      }
      const testPath = resolveRepositoryPath(root, test.path);
      if (!testPath || !existsSync(testPath)) {
        errors.push(`${prefix}.coverage.tests[${testIndex}].path does not exist: ${test.path}`);
      }
      if (coverage.status === 'covered' && !test.path.startsWith('zeppelin-web-angular/e2e/tests/notebook/')) {
        errors.push(`${prefix}.coverage.tests[${testIndex}].path must be in the notebook E2E suite`);
      }
      if (typeof test.title !== 'string' || !test.title.includes(scenario.id)) {
        errors.push(`${prefix}.coverage.tests[${testIndex}].title must contain ${scenario.id}`);
      } else if (!testDeclaresExecutableTitle(root, test)) {
        errors.push(
          `${prefix}.coverage.tests[${testIndex}].title is not declared by an executable test() in ${test.path}`
        );
      }
      if (!Array.isArray(test.projects) || test.projects.length === 0) {
        errors.push(`${prefix}.coverage.tests[${testIndex}].projects must be a non-empty array`);
      } else {
        for (const project of test.projects) {
          if (!allowedProjects.has(project)) {
            errors.push(`${prefix}.coverage.tests[${testIndex}].projects contains an invalid project: ${project}`);
          }
        }
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
