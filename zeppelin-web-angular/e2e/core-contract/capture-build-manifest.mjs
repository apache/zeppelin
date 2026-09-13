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

import { createHash } from 'node:crypto';
import {
  existsSync,
  lstatSync,
  readlinkSync,
  readdirSync,
  readFileSync,
  realpathSync,
  statSync,
  writeFileSync
} from 'node:fs';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';

const artifactInputs = [
  { path: '.', selection: 'root-jars' },
  { path: '.', selection: 'all-wars' },
  { path: 'bin' },
  { path: 'conf' },
  { path: 'lib', required: false },
  { path: 'lib/interpreter', required: false },
  { path: 'zeppelin-interpreter/target/classes' },
  { path: 'zeppelin-server/target/classes' },
  { path: 'zeppelin-interpreter/target/lib', required: false },
  { path: 'zeppelin-server/target/lib' },
  { path: 'zeppelin-web/target/lib', required: false },
  { path: 'zeppelin-web-angular/target/lib', required: false },
  { path: 'zeppelin-server/target/test-classes' },
  { path: 'interpreter' },
  { path: 'zeppelin-interpreter-shaded/target' },
  { path: 'zeppelin-web-angular/dist/zeppelin' },
  { path: 'shell/target/classes' }
];
const license =
  'Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. ' +
  'See the NOTICE file distributed with this work for additional information regarding copyright ownership. ' +
  'The ASF licenses this file to You under the Apache License, Version 2.0.';

const hashInput = (root, input) => {
  const { path: relativeRoot, required = true, selection = 'tree' } = input;
  const absoluteRoot = resolve(root, relativeRoot);
  const rootStat = statSync(absoluteRoot, { throwIfNoEntry: false });
  if (!rootStat?.isDirectory()) {
    if (required) throw new Error(`required build output is missing: ${relativeRoot}`);
    const digest = createHash('sha256').update('missing\0').digest('hex');
    return { exists: false, fileCount: 0, path: relativeRoot, selection, sha256: digest };
  }
  const files = [];
  const activeDirectories = new Set();
  const visit = (directory, logicalDirectory = '') => {
    const realDirectory = realpathSync(directory);
    if (activeDirectories.has(realDirectory)) throw new Error(`build output contains a symlink cycle: ${relativeRoot}`);
    activeDirectories.add(realDirectory);
    for (const entry of readdirSync(directory, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
      if (selection === 'root-jars' && logicalDirectory === '' && !entry.name.toLowerCase().endsWith('.jar')) continue;
      const absolutePath = resolve(directory, entry.name);
      const logicalPath = logicalDirectory ? `${logicalDirectory}/${entry.name}` : entry.name;
      const linkStat = lstatSync(absolutePath);
      const targetStat = statSync(absolutePath);
      if (targetStat.isDirectory()) {
        if (selection === 'root-jars') continue;
        visit(absolutePath, logicalPath);
      } else if (targetStat.isFile()) {
        if (selection === 'all-wars' && !entry.name.toLowerCase().endsWith('.war')) continue;
        files.push({ absolutePath, logicalPath, symlink: linkStat.isSymbolicLink() });
      } else {
        throw new Error(`unsupported build output entry: ${relativeRoot}/${logicalPath}`);
      }
    }
    activeDirectories.delete(realDirectory);
  };
  visit(absoluteRoot);
  const digest = createHash('sha256');
  for (const file of files) {
    digest.update(file.logicalPath);
    digest.update('\0');
    digest.update(file.symlink ? 'symlink\0' : 'file\0');
    if (file.symlink) {
      digest.update(readlinkSync(file.absolutePath));
      digest.update('\0');
    }
    digest.update(readFileSync(file.absolutePath));
    digest.update('\0');
  }
  return { exists: true, fileCount: files.length, path: relativeRoot, selection, sha256: digest.digest('hex') };
};

const git = (root, ...args) => execFileSync('git', ['-C', root, ...args], { encoding: 'utf8' }).trim();
const sortValue = value => {
  if (Array.isArray(value)) return value.map(sortValue);
  if (value && typeof value === 'object')
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map(key => [key, sortValue(value[key])])
    );
  return value;
};
const canonical = value => `${JSON.stringify(sortValue(value), null, 2)}\n`;
const withId = body => ({
  _license: license,
  ...body,
  manifestId: createHash('sha256').update(canonical(body)).digest('hex')
});

const hashTrackedSource = root => {
  const output = execFileSync('git', ['-C', root, 'ls-tree', '-rz', '--full-tree', 'HEAD'], { encoding: 'buffer' });
  const digest = createHash('sha256');
  let count = 0;
  for (const entry of output.toString('utf8').split('\0').filter(Boolean)) {
    const tab = entry.indexOf('\t');
    const [mode, type] = entry.slice(0, tab).split(' ');
    if (type !== 'blob') continue;
    const path = entry.slice(tab + 1);
    const absolutePath = resolve(root, path);
    const stat = lstatSync(absolutePath, { throwIfNoEntry: false });
    if (!stat) throw new Error(`tracked source is missing: ${path}`);
    const content = stat.isSymbolicLink() ? Buffer.from(readlinkSync(absolutePath)) : readFileSync(absolutePath);
    const actual = createHash('sha1').update(`blob ${content.length}\0`).update(content).digest('hex');
    const expected = entry.slice(0, tab).split(' ')[2];
    if (actual !== expected) throw new Error(`tracked source differs from HEAD: ${path}`);
    digest.update(mode).update('\0').update(path).update('\0').update(content).update('\0');
    count += 1;
  }
  return { fileCount: count, sha256: digest.digest('hex') };
};

const findMatchingFiles = (root, pattern) => {
  const matches = [];
  const active = new Set();
  const visit = (directory, logical = '') => {
    const real = realpathSync(directory);
    if (active.has(real)) throw new Error('launcher fallback search contains a symlink cycle');
    active.add(real);
    for (const entry of readdirSync(directory, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
      const absolute = resolve(directory, entry.name);
      const relative = logical ? `${logical}/${entry.name}` : entry.name;
      const target = statSync(absolute);
      if (target.isDirectory()) visit(absolute, relative);
      else if (target.isFile() && pattern.test(entry.name)) matches.push(relative);
    }
    active.delete(real);
  };
  visit(root);
  return matches;
};

const launchTargets = root => {
  const select = (directory, pattern, label) => {
    if (existsSync(resolve(root, directory))) return { kind: 'directory', path: directory };
    const candidates = findMatchingFiles(root, pattern);
    if (candidates.length > 1) throw new Error(`${label} fallback is ambiguous: ${candidates.join(', ')}`);
    return candidates.length === 1 ? { kind: 'war', path: candidates[0] } : { kind: 'missing', path: '' };
  };
  return {
    angularWeb: select('zeppelin-web-angular/dist/zeppelin', /^zeppelin-web-angular.*\.war$/, 'Angular WAR'),
    classicWeb: select('zeppelin-web/dist', /^zeppelin-web-[0-9].*\.war$/, 'classic WAR')
  };
};
const createManifest = root => {
  const sourceCommit = git(root, 'rev-parse', 'HEAD');
  const baseCommit = git(root, 'rev-parse', 'origin/master');
  if (sourceCommit !== baseCommit)
    throw new Error(`build checkout ${sourceCommit} must equal origin/master ${baseCommit}`);
  if (git(root, 'status', '--porcelain', '--untracked-files=no')) throw new Error('build checkout has tracked changes');
  return withId({
    artifacts: artifactInputs.map(input => hashInput(root, input)),
    baseCommit,
    launchTargets: launchTargets(root),
    sourceCommit,
    sourceTree: hashTrackedSource(root),
    version: 3
  });
};

const [command, manifestPath, rootArgument] = process.argv.slice(2);
if (!['create', 'id', 'verify'].includes(command) || !manifestPath || (command !== 'id' && !rootArgument)) {
  throw new Error('usage: capture-build-manifest.mjs create|verify <manifest> <build-root> | id <manifest>');
}
if (command === 'create') {
  const manifest = createManifest(resolve(rootArgument));
  writeFileSync(resolve(manifestPath), canonical(manifest));
} else {
  const manifest = JSON.parse(readFileSync(resolve(manifestPath), 'utf8'));
  if (command === 'id') {
    process.stdout.write(`${manifest.manifestId}\n`);
  } else {
    const expected = createManifest(resolve(rootArgument));
    if (canonical(manifest) !== canonical(expected)) {
      throw new Error('build manifest does not match the current source and launched artifacts');
    }
  }
}
