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

const readCapture = file => JSON.parse(readFileSync(path.resolve('e2e/fixtures/notebook-lifecycle', file), 'utf8'));

const receivedEnvelopes = capture =>
  capture.records
    .filter(record => record.kind === 'websocket' && record.websocket.direction === 'receive')
    .map(record => JSON.parse(record.websocket.payloadText));

test('committed lifecycle inventory validates every capture and its Apache master provenance', () => {
  const manifest = readCapture('manifest.json');
  const requiredFiles = [
    'structural.json',
    'revision-reconnect.json',
    'association.json',
    'collaboration-anonymous.json',
    'collaboration-auth.json',
    'commit-request-loss.json',
    'commit-reply-loss.json'
  ];
  assert.deepEqual(manifest.fixtures.map(entry => entry.file).sort(), requiredFiles.sort());

  for (const entry of manifest.fixtures) {
    assert.equal(entry.status, 'supported', `${entry.file}: ${entry.reason ?? 'capture must pass before committing'}`);

    const capture = readCapture(entry.file);
    assert.deepEqual(validateLifecycleFixture(capture), [], entry.file);
    assert.equal(capture.metadata.source.repository, 'apache/zeppelin');
    assert.match(capture.metadata.source.commit, /^[a-f0-9]{40}$/);
    assert.match(capture.metadata.source.serverCommit, /^[a-f0-9]{7,40}$/);
    assert.ok(capture.metadata.source.commit.startsWith(capture.metadata.source.serverCommit));

    const environment = capture.metadata.environment;
    assert.equal(environment.authentication, entry.file === 'collaboration-auth.json' ? 'auth' : 'anonymous');
    assert.match(environment.paragraphStatusProgress, /^(true|false)$/);
    assert.equal(environment.browser.name, 'chromium');
    assert.ok(environment.browser.version);
    assert.ok(new URL(environment.origin).port);
    assert.ok(Number.isInteger(environment.serverPort));
    assert.equal(environment.interpreterExecution, 'none');

    for (const directory of ['notebook', 'search', 'recovery', 'logs', 'pid']) {
      assert.ok(environment.directories[directory]);
    }
    assert.ok(environment.serverPidFile);
  }
});

test('structural captures keep REST full-note authority separate from granular WebSocket authority', () => {
  const capture = readCapture('structural.json');
  assert.deepEqual(capture.metadata.authoritativeInputs[0].frames, ['NOTE']);

  const operations = new Set(
    capture.records
      .filter(record => record.kind === 'websocket')
      .map(record => JSON.parse(record.websocket.payloadText).op)
  );

  for (const operation of [
    'INSERT_PARAGRAPH',
    'MOVE_PARAGRAPH',
    'COPY_PARAGRAPH',
    'PARAGRAPH_REMOVE',
    'COMMIT_PARAGRAPH',
    'PARAGRAPH_ADDED',
    'PARAGRAPH_MOVED',
    'PARAGRAPH_REMOVED'
  ]) {
    assert.ok(operations.has(operation), operation);
  }

  for (const method of ['POST', 'DELETE']) {
    assert.ok(
      capture.records.some(
        record =>
          record.kind === 'rest' &&
          record.rest.direction === 'request' &&
          record.rest.request.method === method &&
          record.rest.request.url.includes('/paragraph')
      )
    );
  }

  const commit = capture.records.find(
    record =>
      record.kind === 'websocket' &&
      record.websocket.direction === 'send' &&
      JSON.parse(record.websocket.payloadText).op === 'COMMIT_PARAGRAPH'
  );
  const envelope = JSON.parse(commit.websocket.payloadText);
  assert.ok(receivedEnvelopes(capture).some(reply => reply.op === 'PARAGRAPH' && reply.msgId === envelope.msgId));
});

test('missing commit acknowledgement requires canonical refetch to distinguish request loss from reply loss', () => {
  const lostRequest = readCapture('commit-request-loss.json');
  const lostReply = readCapture('commit-reply-loss.json');
  assert.equal(lostRequest.metadata.commitLoss.localDraft, lostReply.metadata.commitLoss.localDraft);
  assert.notEqual(lostRequest.metadata.commitLoss.canonicalText, lostReply.metadata.commitLoss.canonicalText);

  for (const capture of [lostRequest, lostReply]) {
    const commit = capture.records.find(
      record =>
        record.kind === 'websocket' &&
        record.websocket.direction === 'send' &&
        JSON.parse(record.websocket.payloadText).op === 'COMMIT_PARAGRAPH'
    );
    const commitId = JSON.parse(commit.websocket.payloadText).msgId;
    const envelopes = receivedEnvelopes({
      ...capture,
      records: capture.records.filter(
        record =>
          !capture.metadata.commitLoss.faults.some(fault => fault.sequence === record.sequence && fault.copies === 0)
      )
    });
    assert.equal(
      envelopes.some(frame => frame.op === 'PARAGRAPH' && frame.msgId === commitId),
      false
    );
    const finalNote = envelopes.findLast(frame => frame.op === 'NOTE').data.note;
    assert.equal(finalNote.paragraphs[0].text, capture.metadata.commitLoss.canonicalText);
    assert.ok(
      capture.records.some(
        record =>
          record.kind === 'rest' &&
          record.rest.direction === 'response' &&
          record.rest.bodyJson?.body?.paragraphs?.[0]?.text === capture.metadata.commitLoss.canonicalText
      )
    );
  }
});

test('route delivery validation rejects missing occurrences, invalid release boundaries and undeclared reorders', () => {
  const captured = readCapture('association.json');
  assert.deepEqual(validateLifecycleFixture(captured), []);

  const missing = globalThis.structuredClone(captured);
  missing.metadata.routeTransition.deliveredSequences.pop();
  assert.match(validateLifecycleFixture(missing).join(), /every upstream receive/);

  const wrongBoundary = globalThis.structuredClone(captured);
  wrongBoundary.metadata.routeTransition.releaseAfterSequence = wrongBoundary.metadata.routeTransition.heldNoteSequence;
  assert.match(validateLifecycleFixture(wrongBoundary).join(), /release boundary/);

  const reordered = globalThis.structuredClone(captured);
  const sequences = reordered.metadata.routeTransition.deliveredSequences;
  [sequences[0], sequences[1]] = [sequences[1], sequences[0]];
  assert.match(validateLifecycleFixture(reordered).join(), /beyond the declared/);
});
