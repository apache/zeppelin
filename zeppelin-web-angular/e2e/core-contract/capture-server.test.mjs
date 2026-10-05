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
import { spawn, spawnSync } from 'node:child_process';
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  writeFileSync
} from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

const script = path.resolve('e2e/core-contract/capture-server.sh');
const stub = path.resolve('e2e/core-contract/capture-stub-zeppelin.mjs');

const temporaryRoots = [];
process.on('exit', () => {
  for (const root of temporaryRoots) {
    rmSync(root, { force: true, recursive: true });
  }
});

for (const [name, whitespace] of [
  ['space', ' '],
  ['tab', '\t'],
  ['newline', '\n']
]) {
  test(`capture server rejects ${name} in a root before creating files or launching`, () => {
    const parent = createRoot();
    const root = path.join(parent.root, `capture${whitespace}root`);
    const launched = path.join(parent.root, 'launched');
    const result = run(['start', '--root', root, '--port', String(parent.zeppelinPort)], {
      CAPTURE_ZEPPELIN_COMMAND: `touch '${launched}'`
    });
    assert.equal(result.status, 2, result.stderr);
    assert.match(result.stderr, /path must not contain whitespace/);
    assert.equal(existsSync(root), false);
    assert.equal(existsSync(launched), false);
  });
}

for (const existing of [false, true]) {
  test(`capture server rejects a canonical whitespace root (existing=${existing}) before side effects`, () => {
    const parent = createRoot();
    const target = path.join(parent.root, 'directory with spaces');
    mkdirSync(target);
    const alias = path.join(parent.root, 'alias');
    symlinkSync(target, alias);
    const root = existing ? alias : path.join(alias, 'new-root');
    const launched = path.join(parent.root, 'launched');
    const result = run(['start', '--root', root, '--port', String(parent.zeppelinPort)], {
      CAPTURE_ZEPPELIN_COMMAND: `touch '${launched}'`
    });
    assert.equal(result.status, 2, result.stderr);
    assert.match(result.stderr, /path must not contain whitespace/);
    assert.equal(existsSync(path.join(target, 'new-root')), false);
    assert.equal(existsSync(path.join(target, '.capture-operation-lock')), false);
    assert.equal(existsSync(path.join(target, '.zeppelin-capture-root')), false);
    assert.equal(existsSync(launched), false);
  });
}

test('capture server rejects a repository path with whitespace before creating the root', () => {
  const parent = createRoot();
  const directory = path.join(parent.root, 'repo with spaces', 'zeppelin-web-angular', 'e2e', 'core-contract');
  mkdirSync(directory, { recursive: true });
  const copy = path.join(directory, 'capture-server.sh');
  writeFileSync(copy, readFileSync(script));
  const root = path.join(parent.root, 'capture');
  const result = spawnSync('bash', [copy, 'start', '--root', root], { encoding: 'utf8' });
  assert.equal(result.status, 2, result.stderr);
  assert.match(result.stderr, /repository path must not contain whitespace/);
  assert.equal(existsSync(root), false);
});

for (const action of ['start', 'stop']) {
  test(`capture server refuses concurrent ${action} while startup owns the root`, async () => {
    const root = createRoot();
    const bin = path.join(root.root, 'bin');
    mkdirSync(bin);
    const gate = path.join(root.root, 'gate');
    assert.equal(spawnSync('mkfifo', [gate]).status, 0);
    writeFileSync(
      path.join(bin, 'mkdir'),
      `#!/bin/bash
if [[ "$2" == */conf ]]; then
  echo CAPTURE_TEST_BARRIER
  read -r release < '${gate}'
fi
exec /bin/mkdir "$@"
`,
      { mode: 0o755 }
    );
    const first = spawn('bash', [script, 'start', '--root', root.root, '--port', String(root.zeppelinPort)], {
      env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, CAPTURE_ZEPPELIN_COMMAND: `node ${stub}` },
      stdio: ['ignore', 'pipe', 'pipe']
    });
    let errors = '';
    let barrierReached = false;
    let startStatus;
    let stopped;
    first.stderr.on('data', chunk => {
      errors += chunk;
    });
    const finished = new Promise(resolve => first.on('exit', resolve));
    try {
      await new Promise((resolve, reject) => {
        let output = '';
        const timeout = setTimeout(() => reject(new Error(`startup barrier timed out: ${errors}`)), 10000);
        first.stdout.on('data', chunk => {
          output += chunk;
          if (output.includes('CAPTURE_TEST_BARRIER')) {
            clearTimeout(timeout);
            barrierReached = true;
            resolve();
          }
        });
      });
      const result = run([action, '--root', root.root, '--port', String(freePortSync())], {
        CAPTURE_ZEPPELIN_COMMAND: `node ${stub}`
      });
      assert.equal(result.status, 1, `concurrent ${action} succeeded: ${result.stdout}`);
      assert.match(result.stderr, /in progress/);
      assert.equal(readFileSync(path.join(root.root, 'zeppelin.pid'), 'utf8').trim(), 'starting');
    } finally {
      if (barrierReached) writeFileSync(gate, 'release\n');
      else first.kill('SIGKILL');
      startStatus = await finished;
      stopped = run(['stop', '--root', root.root]);
      // A regressed concurrent start can strand a second server outside the PID file.
      const survivors = spawnSync('pgrep', ['-f', marker(root.root)], { encoding: 'utf8' });
      for (const pid of survivors.stdout.trim().split('\n').filter(Boolean)) {
        try {
          process.kill(Number(pid), 'SIGKILL');
        } catch {
          /* already exited */
        }
      }
    }
    assert.equal(startStatus, 0, errors);
    assert.equal(stopped.status, 0, stopped.stderr);
  });
}

test('capture server refuses startup when listener ownership cannot be verified', () => {
  const root = createRoot();
  const environment = path.join(root.root, 'bash-env');
  writeFileSync(
    environment,
    `command() {
  if [[ "$1" == -v && "$2" == lsof ]]; then return 1; fi
  builtin command "$@"
}
`
  );
  try {
    const result = run(['start', '--root', root.root, '--port', String(root.zeppelinPort)], {
      BASH_ENV: environment,
      CAPTURE_ZEPPELIN_COMMAND: `node ${stub}`
    });
    assert.equal(result.status, 1, result.stdout);
    assert.match(result.stderr, /lsof.*required/);
    assert.equal(existsSync(path.join(root.root, 'zeppelin.pid')), false);
  } finally {
    if (existsSync(path.join(root.root, 'zeppelin.pid'))) stop(root);
  }
});

test('capture server replaces inherited storage, binding and JVM configuration', () => {
  const root = createRoot();
  const probe = path.join(root.root, 'probe.mjs');
  const observed = path.join(root.root, 'environment.json');
  const inherited = {
    ZEPPELIN_NOTEBOOK_STORAGE: 'external.NotebookRepo',
    ZEPPELIN_ADDR: '0.0.0.0',
    ZEPPELIN_SEARCH_INDEX_PATH: '/outside/index',
    ZEPPELIN_RECOVERY_DIR: '/outside/recovery',
    ZEPPELIN_NOTEBOOK_GIT_REMOTE_URL: 'https://example.invalid/notebooks.git',
    ZEPPELIN_JAVA_OPTS: '-Dzeppelin.notebook.storage=external.NotebookRepo',
    JAVA_OPTS: '-Dzeppelin.search.index.path=/outside/index',
    JAVA_TOOL_OPTIONS: '-Dzeppelin.recovery.dir=/outside/recovery',
    _JAVA_OPTIONS: '-Dzeppelin.server.addr=0.0.0.0',
    JDK_JAVA_OPTIONS: '-Dzeppelin.notebook.storage=external.NotebookRepo',
    CLASSPATH: '/outside/classes'
  };
  writeFileSync(
    probe,
    `import { writeFileSync } from 'node:fs';
writeFileSync(${JSON.stringify(observed)}, JSON.stringify(Object.fromEntries(
  ${JSON.stringify(Object.keys(inherited))}.map(key => [key, process.env[key] ?? null])
)));
await import(${JSON.stringify(stub)});
`
  );
  const result = run(['start', '--root', root.root, '--port', String(root.zeppelinPort)], {
    ...inherited,
    CAPTURE_ZEPPELIN_COMMAND: `node ${probe}`
  });
  try {
    assert.equal(result.status, 0, result.stderr);
    const env = JSON.parse(readFileSync(observed, 'utf8'));
    assert.equal(env.ZEPPELIN_NOTEBOOK_STORAGE, 'org.apache.zeppelin.notebook.repo.VFSNotebookRepo');
    assert.equal(env.ZEPPELIN_ADDR, '127.0.0.1');
    for (const key of Object.keys(inherited).filter(
      key => !['ZEPPELIN_NOTEBOOK_STORAGE', 'ZEPPELIN_ADDR', 'ZEPPELIN_JAVA_OPTS'].includes(key)
    )) {
      assert.equal(env[key], null, `${key} escaped environment isolation`);
    }
    assert.ok(env.ZEPPELIN_JAVA_OPTS.includes(`-Dzeppelin.search.index.path=${root.root}/index`));
    assert.ok(!env.ZEPPELIN_JAVA_OPTS.includes('external.NotebookRepo'));
  } finally {
    if (existsSync(path.join(root.root, 'zeppelin.pid'))) stop(root);
  }
});

test('capture-server starts and stops a server in its own root', () => {
  const root = createRoot();

  start(root);
  stop(root);

  assert.equal(existsSync(path.join(root.root, '.zeppelin-capture-root')), true);
  assert.equal(existsSync(path.join(root.root, 'zeppelin.pid')), false);
});

test('capture-server writes anonymous and auth config in an isolated temp root', () => {
  const anonymous = createRoot();
  const auth = createRoot();

  start(anonymous);
  stop(anonymous);
  start(auth, { mode: 'auth' });
  stop(auth);

  assert.equal(existsSync(path.join(anonymous.root, 'conf/shiro.ini')), false);
  assert.equal(existsSync(path.join(auth.root, 'conf/shiro.ini')), true);
});

test('capture-server reports explicit port conflicts', async () => {
  const server = await listen();
  const root = createRoot();

  try {
    const result = run(['start', '--root', root.root, '--port', String(server.address().port)]);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /port .* is already in use/);
  } finally {
    await close(server);
  }
});

test('capture server does not inherit a Hadoop setting from the developer shell', () => {
  const root = createRoot();
  const result = run(['start', '--root', root.root, '--port', String(root.zeppelinPort)], {
    CAPTURE_ZEPPELIN_COMMAND: `test "$USE_HADOOP" = false && node ${stub}`,
    USE_HADOOP: 'true'
  });
  assert.equal(result.status, 0, result.stderr);
  stop(root);
});

test('capture server stops a compound command without orphaning the real server', () => {
  const root = createRoot();
  // Killing only the wrapper would leave its server holding the port.
  const started = run(['start', '--root', root.root, '--port', String(root.zeppelinPort)], {
    CAPTURE_ZEPPELIN_COMMAND: `test "$USE_HADOOP" = false && node ${stub}`
  });
  assert.equal(started.status, 0, started.stderr);

  const stopped = run(['stop', '--root', root.root, '--port', String(root.zeppelinPort)]);
  assert.equal(stopped.status, 0, stopped.stderr);

  const survivors = spawnSync('pgrep', ['-f', `-Dzeppelin.capture.root=${root.root}`], { encoding: 'utf8' });
  assert.equal(survivors.error, undefined, 'pgrep has to run for this assertion to mean anything');
  assert.equal((survivors.stdout ?? '').trim(), '', 'the wrapped server must not survive stop');
  assert.equal(existsSync(path.join(root.root, 'zeppelin.pid')), false, 'a clean stop removes the pid file');
});

test('auth mode reports the shiro config the login helper must use', () => {
  const root = createRoot();
  const started = run(['start', '--root', root.root, '--mode', 'auth', '--port', String(root.zeppelinPort)], {
    CAPTURE_ZEPPELIN_COMMAND: `node ${stub}`
  });
  assert.equal(started.status, 0, started.stderr);

  // The login helper needs this capture's shiro.ini via ZEPPELIN_E2E_SHIRO_INI.
  const expected = path.join(root.root, 'conf', 'shiro.ini');
  assert.ok(existsSync(expected), 'auth mode must install shiro.ini in the capture root');
  assert.match(started.stdout, new RegExp(expected.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  assert.notEqual(expected, path.resolve('..', 'conf', 'shiro.ini'));

  stop(root);
});

test('anonymous mode reports no shiro config and installs none', () => {
  const root = createRoot();
  const started = run(['start', '--root', root.root, '--port', String(root.zeppelinPort)], {
    CAPTURE_ZEPPELIN_COMMAND: `node ${stub}`
  });
  assert.equal(started.status, 0, started.stderr);
  assert.equal(existsSync(path.join(root.root, 'conf', 'shiro.ini')), false);
  assert.doesNotMatch(started.stdout, /shiro config/);

  stop(root);
});

test('capture server start clears a stale pid file instead of refusing forever', () => {
  const root = createRoot();
  // PID 1 represents a live, unrelated process whose stale record must not block reuse.
  writeFileSync(path.join(root.root, 'zeppelin.pid'), '1');

  const started = run(['start', '--root', root.root, '--port', String(root.zeppelinPort)], {
    CAPTURE_ZEPPELIN_COMMAND: `node ${stub}`
  });
  assert.equal(started.status, 0, started.stderr);
  stop(root);
});

test('capture server refuses to stop a server whose root only shares a prefix', () => {
  const root = createRoot();
  markRoot(root);
  // A shared path prefix must not grant ownership of another capture's process.
  const decoy = spawnMarked(root, `${root.root}-other`);
  try {
    writeFileSync(path.join(root.root, 'zeppelin.pid'), String(decoy.pid));
    const result = run(['stop', '--root', root.root, '--port', String(root.zeppelinPort)]);

    assert.equal(result.status, 1, result.stdout);
    assert.match(result.stderr, /command does not match/);
    assert.equal(existsSync(path.join(root.root, 'zeppelin.pid')), true, 'the pid file has to survive a refusal');
    assert.equal(alive(decoy.pid), true, "the other capture's server was killed");
  } finally {
    decoy.kill('SIGKILL');
  }
});

test('capture server stops a surviving child after its process leader is gone', async () => {
  const root = createRoot();
  markRoot(root);
  // The surviving child must be stopped even after its group leader exits.
  const leader = spawn(
    'bash',
    ['-c', `'${process.execPath}' '${childScript(root)}' '${marker(root.root)}' & echo $! > '${root.root}/child.pid'`],
    {
      detached: true,
      stdio: 'ignore'
    }
  );
  const leaderPid = leader.pid;
  await new Promise(resolve => leader.on('exit', resolve));
  const childPid = Number(readFileSync(path.join(root.root, 'child.pid'), 'utf8').trim());
  assert.equal(alive(childPid), true, 'the child has to outlive its leader for this test to mean anything');

  try {
    writeFileSync(path.join(root.root, 'zeppelin.pid'), String(leaderPid));
    const result = run(['stop', '--root', root.root, '--port', String(root.zeppelinPort)]);

    assert.equal(result.status, 0, result.stderr);
    assert.equal(alive(childPid), false, 'stop left the child running');
    assert.equal(existsSync(path.join(root.root, 'zeppelin.pid')), false);
  } finally {
    try {
      process.kill(childPid, 'SIGKILL');
    } catch {
      // Already exited.
    }
  }
});

const marker = root => `-Dzeppelin.capture.root=${root}`;

// Use a script file so Node treats the marker as an argument, not an option to node -e.
function childScript(root) {
  const file = path.join(root.root, 'stay-alive.mjs');
  writeFileSync(file, 'setTimeout(() => {}, 60000);\n');
  return file;
}

function spawnMarked(root, markerRoot) {
  return spawn(process.execPath, [childScript(root), marker(markerRoot)], { stdio: 'ignore' });
}

function markRoot(root) {
  writeFileSync(path.join(root.root, '.zeppelin-capture-root'), `root=${root.root}\n`);
}

function alive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

test('capture server refuses a second start while the first is still running', () => {
  const root = createRoot();
  const second = { root: root.root, zeppelinPort: freePortSync() };
  start(root);
  const recorded = readFileSync(path.join(root.root, 'zeppelin.pid'), 'utf8').trim();

  try {
    // A different port isolates the PID ownership check from port conflict detection.
    const result = run(['start', '--root', second.root, '--port', String(second.zeppelinPort)], {
      CAPTURE_ZEPPELIN_COMMAND: `node ${stub}`
    });

    assert.equal(result.status, 1, result.stdout);
    assert.match(result.stderr, /still running/);
    assert.equal(
      readFileSync(path.join(root.root, 'zeppelin.pid'), 'utf8').trim(),
      recorded,
      'the pid file was overwritten'
    );
  } finally {
    stop(root);
  }
});

test('capture server stops a root started on another port without being told the port', () => {
  const root = createRoot();
  // Stop must use the recorded port, not the default port an unrelated process may hold.
  start(root);

  const result = run(['stop', '--root', root.root]);

  assert.equal(result.status, 0, result.stderr);
  assert.equal(existsSync(path.join(root.root, 'zeppelin.pid')), false);
});

function start(root, options = {}) {
  const result = run(
    ['start', '--root', root.root, '--mode', options.mode ?? 'anonymous', '--port', String(root.zeppelinPort)],
    { CAPTURE_ZEPPELIN_COMMAND: `node ${stub}` }
  );
  assert.equal(result.status, 0, result.stderr);
}

function stop(root) {
  const result = run(['stop', '--root', root.root, '--port', String(root.zeppelinPort)]);
  assert.equal(result.status, 0, result.stderr);
}

function run(args, env = {}) {
  return spawnSync('bash', [script, ...args], {
    cwd: path.resolve('.'),
    encoding: 'utf8',
    env: { ...process.env, ...env }
  });
}

function createRoot() {
  const created = mkdtempSync(path.join(os.tmpdir(), 'zeppelin-capture-'));
  temporaryRoots.push(created);
  // Match pwd -P when writing markers; macOS temporary directories may resolve through symlinks.
  return { root: realpathSync(created), zeppelinPort: freePortSync() };
}

function freePortSync() {
  const result = spawnSync(
    process.execPath,
    [
      '-e',
      "require('net').createServer().listen(0, '127.0.0.1', function () { console.log(this.address().port); this.close(); })"
    ],
    {
      encoding: 'utf8'
    }
  );
  return Number(result.stdout.trim());
}

function listen() {
  return new Promise(resolve => {
    const server = http.createServer();
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

function close(server) {
  return new Promise(resolve => server.close(resolve));
}

test('capture server retains the PID claim when termination cannot stop its process', async () => {
  const root = createRoot();
  markRoot(root);
  const child = spawnMarked(root, root.root);
  const exited = new Promise(resolve => child.once('exit', resolve));
  const pidFile = path.join(root.root, 'zeppelin.pid');
  const shellEnv = path.join(root.root, 'no-signals.sh');
  // Disable signals and polling delays, but use real ps to verify the owned process remains alive.
  writeFileSync(shellEnv, 'kill() { return 0; }\nsleep() { :; }\n');
  writeFileSync(pidFile, String(child.pid));
  try {
    const result = run(['stop', '--root', root.root], { BASH_ENV: shellEnv });
    assert.notEqual(result.status, 0, 'stop must fail when its process remains alive');
    assert.match(result.stderr, /failed to stop/);
    assert.equal(readFileSync(pidFile, 'utf8'), String(child.pid));
    assert.equal(alive(child.pid), true);
  } finally {
    child.kill('SIGKILL');
    await exited;
  }
});
