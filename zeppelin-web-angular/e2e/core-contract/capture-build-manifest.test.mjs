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

import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

const script = new URL('./capture-build-manifest.mjs', import.meta.url).pathname;
const roots = [
  'interpreter',
  'bin',
  'conf',
  'zeppelin-server/target/classes',
  'zeppelin-server/target/test-classes',
  'zeppelin-server/target/lib',
  'zeppelin-interpreter/target/classes',
  'zeppelin-interpreter-shaded/target',
  'zeppelin-web-angular/dist/zeppelin',
  'shell/target/classes'
];
const optionalLauncherRoots = [
  'lib',
  'lib/interpreter',
  'zeppelin-interpreter/target/lib',
  'zeppelin-web/dist',
  'zeppelin-web/target/lib',
  'zeppelin-web-angular/target/lib'
];

test('build manifest binds a clean origin/master checkout to the exact launched artifacts', () => {
  const root = mkdtempSync(join(tmpdir(), 'zeppelin-build-manifest-'));
  try {
    execFileSync('git', ['init', '-q', root]);
    execFileSync('git', ['-C', root, 'config', 'user.email', 'fixture@example.test']);
    execFileSync('git', ['-C', root, 'config', 'user.name', 'Fixture Test']);
    for (const directory of roots) {
      mkdirSync(join(root, directory), { recursive: true });
      writeFileSync(join(root, directory, 'artifact'), directory);
    }
    writeFileSync(join(root, 'tracked'), 'source');
    execFileSync('git', ['-C', root, 'add', 'tracked']);
    execFileSync('git', ['-C', root, 'commit', '-qm', 'source']);
    const head = execFileSync('git', ['-C', root, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
    execFileSync('git', ['-C', root, 'update-ref', 'refs/remotes/origin/master', head]);
    const manifest = join(root, 'manifest.json');

    execFileSync(process.execPath, [script, 'create', manifest, root]);
    execFileSync(process.execPath, [script, 'verify', manifest, root]);
    const captured = JSON.parse(readFileSync(manifest, 'utf8'));
    assert.equal(captured.sourceCommit, head);
    assert.equal(captured.baseCommit, head);
    assert.equal(captured.version, 3);
    assert.ok(captured.sourceTree.fileCount > 0);
    assert.match(captured.manifestId, /^[0-9a-f]{64}$/);

    writeFileSync(join(root, roots[0], 'artifact'), 'stale replacement');
    const stale = spawnSync(process.execPath, [script, 'verify', manifest, root], { encoding: 'utf8' });
    assert.notEqual(stale.status, 0);
    assert.match(stale.stderr, /does not match the current source and launched artifacts/);
  } finally {
    rmSync(root, { force: true, recursive: true });
  }
});

test('build manifest rejects added launcher jars and changed symlink targets', () => {
  const root = mkdtempSync(join(tmpdir(), 'zeppelin-build-manifest-runtime-'));
  try {
    execFileSync('git', ['init', '-q', root]);
    execFileSync('git', ['-C', root, 'config', 'user.email', 'fixture@example.test']);
    execFileSync('git', ['-C', root, 'config', 'user.name', 'Fixture Test']);
    for (const directory of [...roots, ...optionalLauncherRoots]) mkdirSync(join(root, directory), { recursive: true });
    for (const directory of roots) writeFileSync(join(root, directory, 'artifact'), directory);
    writeFileSync(join(root, 'runtime-a'), 'runtime-a');
    writeFileSync(join(root, 'runtime-b'), 'runtime-b');
    symlinkSync(join(root, 'runtime-a'), join(root, 'lib', 'runtime.jar'));
    writeFileSync(join(root, 'tracked'), 'source');
    execFileSync('git', ['-C', root, 'add', 'tracked']);
    execFileSync('git', ['-C', root, 'commit', '-qm', 'source']);
    const head = execFileSync('git', ['-C', root, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
    execFileSync('git', ['-C', root, 'update-ref', 'refs/remotes/origin/master', head]);
    const manifest = join(root, 'manifest.json');

    execFileSync(process.execPath, [script, 'create', manifest, root]);
    writeFileSync(join(root, 'extra-runtime.jar'), 'unmanifested runtime input');
    const extraJar = spawnSync(process.execPath, [script, 'verify', manifest, root], { encoding: 'utf8' });
    assert.notEqual(extraJar.status, 0);
    assert.match(extraJar.stderr, /does not match the current source and launched artifacts/);

    rmSync(join(root, 'extra-runtime.jar'));
    rmSync(join(root, 'lib', 'runtime.jar'));
    symlinkSync(join(root, 'runtime-b'), join(root, 'lib', 'runtime.jar'));
    const changedLink = spawnSync(process.execPath, [script, 'verify', manifest, root], { encoding: 'utf8' });
    assert.notEqual(changedLink.status, 0);
    assert.match(changedLink.stderr, /does not match the current source and launched artifacts/);
  } finally {
    rmSync(root, { force: true, recursive: true });
  }
});

test('build manifest rejects fallback WAR additions and symlink retargeting', () => {
  const root = createRepository();
  try {
    const manifest = join(root, 'manifest.json');
    execFileSync(process.execPath, [script, 'create', manifest, root]);
    mkdirSync(join(root, 'fallback'), { recursive: true });
    writeFileSync(join(root, 'fallback', 'zeppelin-web-0.13.0.war'), 'unexpected');
    assert.notEqual(spawnSync(process.execPath, [script, 'verify', manifest, root]).status, 0);
    rmSync(join(root, 'fallback'), { recursive: true });

    writeFileSync(join(root, 'war-a'), 'same bytes');
    writeFileSync(join(root, 'war-b'), 'same bytes');
    symlinkSync(join(root, 'war-a'), join(root, 'zeppelin-web-angular-test.war'));
    execFileSync(process.execPath, [script, 'create', manifest, root]);
    rmSync(join(root, 'zeppelin-web-angular-test.war'));
    symlinkSync(join(root, 'war-b'), join(root, 'zeppelin-web-angular-test.war'));
    assert.notEqual(spawnSync(process.execPath, [script, 'verify', manifest, root]).status, 0);
  } finally {
    rmSync(root, { force: true, recursive: true });
  }
});

test('build manifest WAR search skips VCS metadata and frontend toolchain directories', () => {
  const root = createRepository();
  try {
    const manifest = join(root, 'manifest.json');
    execFileSync(process.execPath, [script, 'create', manifest, root]);
    for (const directory of ['.git/nested', 'node_modules/pkg', 'zeppelin-web-angular/node']) {
      mkdirSync(join(root, directory), { recursive: true });
      writeFileSync(join(root, directory, 'zeppelin-web-0.13.0.war'), 'ignored');
    }
    execFileSync(process.execPath, [script, 'verify', manifest, root]);
  } finally {
    rmSync(root, { force: true, recursive: true });
  }
});

test('build manifest rejects tracked launcher changes hidden with skip-worktree', () => {
  const root = createRepository({ trackLauncher: true });
  try {
    const manifest = join(root, 'manifest.json');
    execFileSync(process.execPath, [script, 'create', manifest, root]);
    execFileSync('git', ['-C', root, 'update-index', '--skip-worktree', 'bin/common.sh']);
    writeFileSync(join(root, 'bin', 'common.sh'), 'changed while hidden');
    const result = spawnSync(process.execPath, [script, 'verify', manifest, root], { encoding: 'utf8' });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /tracked source differs from HEAD/);
  } finally {
    rmSync(root, { force: true, recursive: true });
  }
});

test('build manifest rejects changed classic frontend assets', () => {
  const root = createRepository();
  try {
    const manifest = join(root, 'manifest.json');
    writeFileSync(join(root, 'zeppelin-web', 'dist', 'index.html'), 'captured classic frontend');
    execFileSync(process.execPath, [script, 'create', manifest, root]);
    writeFileSync(join(root, 'zeppelin-web', 'dist', 'index.html'), 'changed classic frontend');

    const changed = spawnSync(process.execPath, [script, 'verify', manifest, root], { encoding: 'utf8' });
    assert.notEqual(changed.status, 0);
    assert.match(changed.stderr, /does not match the current source and launched artifacts/);
  } finally {
    rmSync(root, { force: true, recursive: true });
  }
});

test('build manifest rejects symlinked build outputs that resolve outside the build root', () => {
  const root = createRepository();
  const outside = mkdtempSync(join(tmpdir(), 'zeppelin-build-manifest-outside-'));
  try {
    const manifest = join(root, 'manifest.json');
    writeFileSync(join(outside, 'runtime.jar'), 'outside runtime');
    symlinkSync(join(outside, 'runtime.jar'), join(root, 'lib', 'runtime.jar'));
    const linkedFile = spawnSync(process.execPath, [script, 'create', manifest, root], { encoding: 'utf8' });
    assert.notEqual(linkedFile.status, 0);
    assert.match(linkedFile.stderr, /build output resolves outside the build root: lib\/runtime\.jar/);

    rmSync(join(root, 'lib', 'runtime.jar'));
    symlinkSync(outside, join(root, 'lib', 'external'));
    const linkedDirectory = spawnSync(process.execPath, [script, 'create', manifest, root], { encoding: 'utf8' });
    assert.notEqual(linkedDirectory.status, 0);
    assert.match(linkedDirectory.stderr, /build output resolves outside the build root: lib\/external/);
  } finally {
    rmSync(root, { force: true, recursive: true });
    rmSync(outside, { force: true, recursive: true });
  }
});

function createRepository({ trackLauncher = false } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'zeppelin-build-manifest-adversarial-'));
  execFileSync('git', ['init', '-q', root]);
  execFileSync('git', ['-C', root, 'config', 'user.email', 'fixture@example.test']);
  execFileSync('git', ['-C', root, 'config', 'user.name', 'Fixture Test']);
  for (const directory of [...roots, ...optionalLauncherRoots]) mkdirSync(join(root, directory), { recursive: true });
  for (const directory of roots) writeFileSync(join(root, directory, 'artifact'), directory);
  writeFileSync(join(root, 'bin', 'common.sh'), 'launcher');
  writeFileSync(join(root, 'tracked'), 'source');
  execFileSync('git', ['-C', root, 'add', 'tracked', ...(trackLauncher ? ['bin/common.sh'] : [])]);
  execFileSync('git', ['-C', root, 'commit', '-qm', 'source']);
  const head = execFileSync('git', ['-C', root, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
  execFileSync('git', ['-C', root, 'update-ref', 'refs/remotes/origin/master', head]);
  return root;
}
