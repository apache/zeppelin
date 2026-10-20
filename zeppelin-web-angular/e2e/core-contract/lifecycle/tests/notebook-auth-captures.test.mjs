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
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { validateLifecycleFixture } from '../fixture.mjs';

const readCapture = file => JSON.parse(readFileSync(path.resolve('e2e/fixtures/notebook-auth', file), 'utf8'));

const accountNames = readFileSync(path.resolve('../conf/shiro.ini.template'), 'utf8')
  .split('[users]')[1]
  .split(/^\[/m)[0]
  .split('\n')
  .map(line => line.trim())
  .filter(line => line && !line.startsWith('#') && line.includes('='))
  .map(line => line.split('=')[0].trim());

const frames = (capture, sessionId, direction) =>
  capture.records
    .filter(
      record =>
        record.kind === 'websocket' && record.sessionId === sessionId && record.websocket.direction === direction
    )
    .map(record => ({ sequence: record.sequence, ...JSON.parse(record.websocket.payloadText) }))
    .filter(frame => frame.op !== 'PING');

const rest = (capture, direction, method, url) =>
  capture.records.filter(
    record =>
      record.kind === 'rest' &&
      record.rest.direction === direction &&
      record.rest.request.method === method &&
      (url instanceof RegExp ? url.test(record.rest.request.url) : record.rest.request.url === url)
  );

const statuses = (capture, method, url) => rest(capture, 'response', method, url).map(record => record.rest.status);

const permissions = /^\/api\/notebook\/[^/]+\/permissions$/;

const redirectedFrom = (capture, record) =>
  capture.records.find(
    source =>
      source.kind === 'rest' &&
      source.rest.direction === 'request' &&
      source.sessionId === record.sessionId &&
      source.requestId === record.redirectedFrom
  );

const fixtures = [
  'acl-write.json',
  'acl-write-role-owner.json',
  'acl-write-unauthenticated.json',
  'acl-write-anonymous.json',
  'rest-401-logout.json',
  'rest-401-with-location.json',
  'http-session-expired.json',
  'ws-global-auth-info.json',
  'ws-global-error-info.json',
  'ws-read-correlation.json',
  'ws-session-logout.json',
  'session-expiry-server-restart.json'
];

test('committed authentication inventory validates every capture, its provenance and its redaction', () => {
  const manifest = readCapture('manifest.json');
  assert.deepEqual(manifest.fixtures.map(entry => entry.file).sort(), [...fixtures].sort());

  for (const entry of manifest.fixtures) {
    if (entry.file === 'rest-401-with-location.json') {
      assert.equal(entry.status, 'skipped');
      assert.match(entry.reason, /Location/);
      continue;
    }
    assert.equal(entry.status, 'supported', `${entry.file}: ${entry.reason ?? 'capture must pass before committing'}`);

    const capture = readCapture(entry.file);
    assert.deepEqual(validateLifecycleFixture(capture), [], entry.file);
    assert.equal(capture.metadata.source.repository, 'apache/zeppelin');
    assert.match(capture.metadata.source.commit, /^[a-f0-9]{40}$/);
    assert.ok(capture.metadata.source.commit.startsWith(capture.metadata.source.serverCommit));

    const environment = capture.metadata.environment;
    assert.equal(environment.authentication, entry.file === 'acl-write-anonymous.json' ? 'anonymous' : 'auth');
    assert.equal(environment.browser.name, 'chromium');
    assert.ok(['127.0.0.1', 'localhost'].includes(new URL(environment.origin).hostname));
    assert.equal(environment.directories, undefined, entry.file);
    assert.equal(environment.serverPidFile, undefined, entry.file);

    const text = JSON.stringify(capture);
    assert.doesNotMatch(text, /\/Users\/|\/home\/|\/var\/folders|\/private\/|\/tmp\//, entry.file);
    for (const name of accountNames) assert.doesNotMatch(text, new RegExp(`\\b${name}\\b`), `${entry.file}: ${name}`);
    for (const record of capture.records.filter(value => value.kind === 'rest')) {
      for (const headers of [record.rest.headers ?? {}, record.rest.request.headers]) {
        assert.deepEqual(
          Object.keys(headers).filter(key => /cookie|authorization/i.test(key)),
          [],
          entry.file
        );
      }
    }
  }
});

test('permission writes are decided by the server rule, including an owner named only by role', () => {
  assert.deepEqual(statuses(readCapture('acl-write.json'), 'PUT', permissions), [200, 403]);
  assert.deepEqual(statuses(readCapture('acl-write-role-owner.json'), 'PUT', permissions), [200, 403]);
  assert.deepEqual(statuses(readCapture('acl-write-anonymous.json'), 'PUT', permissions), [200]);
});

test('an unauthenticated write is redirected to the login endpoint, which rejects the method', () => {
  const capture = readCapture('acl-write-unauthenticated.json');
  const [redirect] = rest(capture, 'response', 'PUT', permissions);
  assert.equal(redirect.rest.status, 302);
  assert.equal(redirect.rest.headers.location, '/api/login');
  const [login] = rest(capture, 'request', 'PUT', '/api/login');
  assert.equal(redirectedFrom(capture, login).requestId, redirect.requestId);
  assert.deepEqual(statuses(capture, 'PUT', '/api/login'), [405]);
});

test('a signed-in logout answers 401 without a Location', () => {
  const [logout] = rest(readCapture('rest-401-logout.json'), 'response', 'POST', '/api/login/logout');
  assert.equal(logout.rest.status, 401);
  assert.equal(logout.rest.headers.location, undefined);
});

test('an HTTP-only expiry keeps the socket working and logs out once per rejected request that is not a logout', () => {
  const capture = readCapture('http-session-expired.json');
  const sent = frames(capture, 'role2-member', 'send').filter(frame => frame.op === 'GET_NOTE');
  const notes = frames(capture, 'role2-member', 'receive').filter(frame => frame.op === 'NOTE');
  assert.ok(sent.some(read => notes.some(note => note.msgId === read.msgId)));

  const rejected = rest(capture, 'request', 'GET', '/api/login').filter(
    login =>
      redirectedFrom(capture, login)?.rest.request.url !== '/api/login/logout' &&
      capture.records.some(
        record =>
          record.kind === 'rest' &&
          record.requestId === login.requestId &&
          record.rest.direction === 'response' &&
          record.rest.status === 405
      )
  );
  assert.ok(rejected.length > 0);
  assert.equal(rest(capture, 'request', 'POST', '/api/login/logout').length, rejected.length);
  for (const login of rest(capture, 'request', 'GET', '/api/login')) {
    assert.ok(redirectedFrom(capture, login), 'every login hop names the request it was redirected from');
  }
});

test('a global AUTH_INFO and a global ERROR_INFO carry no msgId and leave the socket usable', () => {
  const authInfo = readCapture('ws-global-auth-info.json');
  const received = frames(authInfo, 'reader', 'receive');
  const denial = received.find(frame => frame.op === 'AUTH_INFO');
  assert.equal(denial.msgId ?? null, null);
  const lastRead = frames(authInfo, 'reader', 'send')
    .filter(frame => frame.op === 'GET_NOTE')
    .at(-1);
  assert.ok(
    received.some(frame => frame.op === 'NOTE' && frame.msgId === lastRead.msgId && frame.sequence > denial.sequence)
  );

  const error = frames(readCapture('ws-global-error-info.json'), 'owner', 'receive').find(
    frame => frame.op === 'ERROR_INFO'
  );
  assert.equal(error.msgId ?? null, null);
});

test('a read failure carries its request msgId while an unrelated global error does not end the read', () => {
  const capture = readCapture('ws-read-correlation.json');
  const reads = frames(capture, 'reader', 'send').filter(frame => frame.op === 'GET_NOTE');
  const received = frames(capture, 'reader', 'receive');
  const global = received.find(frame => frame.op === 'ERROR_INFO' && !frame.msgId);
  const note = received.find(frame => frame.op === 'NOTE' && frame.msgId === reads.at(-3).msgId);
  assert.ok(global.sequence < note.sequence);
  assert.ok(received.some(frame => frame.op === 'ERROR_INFO' && frame.msgId === reads.at(-2).msgId));
  assert.ok(received.some(frame => frame.op === 'AUTH_INFO' && frame.msgId === reads.at(-1).msgId));
});

for (const file of ['ws-session-logout.json', 'session-expiry-server-restart.json']) {
  test(`${file} drops a command on a ticket with no entry and answers the old ticket with SESSION_LOGOUT`, () => {
    const capture = readCapture(file);
    const reads = frames(capture, 'viewer', 'send').filter(frame => frame.op === 'GET_NOTE');
    const [dropped, rejected] = reads.slice(-2);
    const between = frames(capture, 'viewer', 'receive').filter(
      frame => frame.sequence > dropped.sequence && frame.sequence < rejected.sequence
    );
    assert.deepEqual(between, []);
    const logout = frames(capture, 'viewer', 'receive').find(
      frame => frame.op === 'SESSION_LOGOUT' && frame.sequence > rejected.sequence
    );
    assert.equal(logout.msgId ?? null, null);
    assert.deepEqual(statuses(capture, 'POST', '/api/login').at(-1), 200);
  });
}
