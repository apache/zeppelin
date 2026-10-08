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
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';

import { createPlaywrightFixtureAdapter, validateFixture } from './notebook-transport-fixture.mjs';

const fixturePath = name => new URL(`./fixtures/${name}.json`, import.meta.url);
const loadFixture = async name => JSON.parse(await readFile(fixturePath(name), 'utf8'));
const cloneFixture = fixture => JSON.parse(JSON.stringify(fixture));
const envelope = record => JSON.parse(record.websocket.payloadText);
const operations = (fixture, direction) =>
  fixture.records
    .filter(record => record.kind === 'websocket' && (!direction || record.websocket.direction === direction))
    .map(envelope);
const terminalParagraph = fixture =>
  operations(fixture, 'receive')
    .filter(message => message.op === 'PARAGRAPH')
    .findLast(message => ['FINISHED', 'ERROR', 'ABORT'].includes(message.data?.paragraph?.status));
const paragraphTextOutput = paragraph =>
  paragraph?.data?.paragraph?.results?.msg
    ?.filter(message => message.type === 'TEXT')
    .map(message => message.data)
    .join('');

const replay = async fixture => {
  const routeHandlers = [];
  const socketHandlers = [];
  const received = [];
  const adapter = createPlaywrightFixtureAdapter(fixture);
  await adapter.install({
    route: async (_pattern, handler) => routeHandlers.push(handler),
    routeWebSocket: async (_pattern, handler) => socketHandlers.push(handler)
  });
  assert.equal(routeHandlers.length, 1);
  assert.equal(socketHandlers.length, 1);

  const clientHandlers = [];
  socketHandlers[0]({
    onMessage: handler => clientHandlers.push(handler),
    send: payload => received.push(JSON.parse(payload))
  });
  assert.equal(clientHandlers.length, 1);

  let runtimeMessageId = 0;
  for (const record of fixture.records.filter(
    entry => entry.kind === 'websocket' && entry.websocket.direction === 'send'
  )) {
    const message = envelope(record);
    clientHandlers[0](
      JSON.stringify({
        ...message,
        ...(typeof message.msgId === 'string' ? { msgId: `runtime-${++runtimeMessageId}` } : {})
      })
    );
  }
  adapter.assertComplete();
  return received;
};

const resequence = fixture => ({
  ...fixture,
  records: fixture.records.map((record, index) => ({ ...record, sequence: index + 1 }))
});

test('streaming-enabled execution captures and replays progress, update, append and terminal delivery', async () => {
  const fixture = await loadFixture('execution-streaming-enabled');
  assert.deepEqual(validateFixture(fixture), []);
  assert.equal(fixture.metadata.interpreter, 'sh');
  assert.equal(fixture.metadata.configuration['zeppelin.websocket.paragraph_status_progress.enable'], true);

  const captured = operations(fixture, 'receive').map(message => message.op);
  for (const operation of ['PROGRESS', 'PARAGRAPH_UPDATE_OUTPUT', 'PARAGRAPH_APPEND_OUTPUT']) {
    assert.ok(captured.includes(operation), `missing captured ${operation}`);
  }
  assert.ok(['FINISHED', 'ERROR', 'ABORT'].includes(terminalParagraph(fixture)?.data.paragraph.status));

  const replayed = await replay(fixture);
  assert.deepEqual(
    replayed.map(message => message.op),
    captured
  );
  assert.equal(
    terminalParagraph({
      records: replayed.map((message, index) => ({
        kind: 'websocket',
        sequence: index + 1,
        websocket: { direction: 'receive', payloadText: JSON.stringify(message) }
      }))
    })?.data.paragraph.status,
    'FINISHED'
  );
});

test('committed captures bind the canonical source and artifact manifest', async () => {
  const canonical = JSON.parse(await readFile(fixturePath('build-manifest'), 'utf8'));
  const expected = { ...canonical, id: canonical.manifestId };
  delete expected._license;
  delete expected.manifestId;
  for (const name of ['execution-streaming-enabled', 'execution-streaming-disabled', 'execution-cancel']) {
    const fixture = await loadFixture(name);
    assert.deepEqual(fixture.metadata.provenance.buildManifest, expected, name);
    for (const [mutation, message] of [
      [manifest => (manifest.id = '0'.repeat(64)), /buildManifest\.id must match the manifest content/],
      [manifest => (manifest.baseCommit = '0'.repeat(40)), /buildManifest\.baseCommit must match/],
      [manifest => (manifest.sourceTree.sha256 = '0'.repeat(64)), /buildManifest\.id must match the manifest content/],
      [manifest => (manifest.artifacts[0].sha256 = '0'.repeat(64)), /buildManifest\.id must match the manifest content/]
    ]) {
      const changed = cloneFixture(fixture);
      mutation(changed.metadata.provenance.buildManifest);
      assert.match(validateFixture(changed).join('\n'), message, `${name} accepted changed manifest`);
    }
  }
});

test('execution coverage cannot claim absent operations or omit represented operations', async () => {
  const fixture = await loadFixture('execution-streaming-disabled');
  assert.deepEqual(validateFixture(fixture), []);

  const falseCoverage = cloneFixture(fixture);
  falseCoverage.metadata.coveredOperations.push('PROGRESS');
  assert.match(validateFixture(falseCoverage).join('\n'), /exactly match operations represented/);

  const missingCoverage = cloneFixture(fixture);
  missingCoverage.metadata.coveredOperations = missingCoverage.metadata.coveredOperations.filter(
    operation => operation !== 'RUN_PARAGRAPH'
  );
  assert.match(validateFixture(missingCoverage).join('\n'), /exactly match operations represented/);

  const otherInterpreter = cloneFixture(falseCoverage);
  otherInterpreter.metadata.interpreter = 'python';
  otherInterpreter.metadata.provenance.interpreter = 'python';
  assert.match(validateFixture(otherInterpreter).join('\n'), /exactly match operations represented/);
});

test('streaming-disabled execution omits incremental events and retains a terminal paragraph', async () => {
  const fixture = await loadFixture('execution-streaming-disabled');
  assert.deepEqual(validateFixture(fixture), []);
  assert.equal(fixture.metadata.interpreter, 'sh');
  assert.equal(fixture.metadata.configuration['zeppelin.websocket.paragraph_status_progress.enable'], false);

  const received = operations(fixture, 'receive');
  const executionOperations = received.filter(message =>
    ['PROGRESS', 'PARAGRAPH_UPDATE_OUTPUT', 'PARAGRAPH_APPEND_OUTPUT', 'PARAGRAPH'].includes(message.op)
  );
  assert.equal(
    executionOperations.some(message =>
      ['PROGRESS', 'PARAGRAPH_UPDATE_OUTPUT', 'PARAGRAPH_APPEND_OUTPUT'].includes(message.op)
    ),
    false
  );
  const terminal = terminalParagraph(fixture);
  assert.equal(terminal?.data.paragraph.status, 'FINISHED');
  assert.equal(paragraphTextOutput(terminal), 'first\nsecond\n');
  const replayed = await replay(fixture);
  assert.deepEqual(
    replayed.map(message => message.op),
    received.map(message => message.op)
  );
  assert.equal(paragraphTextOutput(replayed.findLast(message => message.op === 'PARAGRAPH')), 'first\nsecond\n');
});

test('cancellation replays run and explicit cancel before an ABORT terminal paragraph', async () => {
  const fixture = await loadFixture('execution-cancel');
  assert.deepEqual(validateFixture(fixture), []);
  assert.deepEqual(
    operations(fixture, 'send')
      .map(message => message.op)
      .filter(operation => operation !== 'GET_NOTE'),
    ['RUN_PARAGRAPH', 'CANCEL_PARAGRAPH']
  );
  assert.equal(terminalParagraph(fixture)?.data.paragraph.status, 'ABORT');
  const replayed = await replay(fixture);
  assert.equal(replayed.findLast(message => message.op === 'PARAGRAPH')?.data.paragraph.status, 'ABORT');
});

test('replay preserves a delayed APPEND after UPDATE without treating one capture order as universal', async () => {
  const fixture = await loadFixture('execution-streaming-enabled');
  const records = [...fixture.records];
  const appendIndex = records.findIndex(record => envelope(record).op === 'PARAGRAPH_APPEND_OUTPUT');
  const [append] = records.splice(appendIndex, 1);
  const terminalIndex = records.findLastIndex(record => envelope(record).op === 'PARAGRAPH');
  records.splice(terminalIndex, 0, append);
  const variant = resequence({ ...fixture, records });

  const opOrder = recordList => recordList.map(record => envelope(record).op);
  assert.notDeepEqual(opOrder(variant.records), opOrder(fixture.records));
  assert.ok(
    records.indexOf(append) < records.findLastIndex(record => envelope(record).op === 'PARAGRAPH'),
    'moved APPEND must still precede the terminal PARAGRAPH'
  );
  assert.deepEqual(validateFixture(variant), []);
  const received = await replay(variant);
  assert.deepEqual(
    received
      .filter(message => ['PARAGRAPH_UPDATE_OUTPUT', 'PARAGRAPH_APPEND_OUTPUT'].includes(message.op))
      .map(message => [message.op, message.data.data]),
    [
      ['PARAGRAPH_UPDATE_OUTPUT', ''],
      ['PARAGRAPH_APPEND_OUTPUT', 'first\n'],
      ['PARAGRAPH_APPEND_OUTPUT', 'second\n']
    ]
  );
});

test('replay delivers an APPEND delayed until after the terminal PARAGRAPH', async () => {
  const fixture = await loadFixture('execution-streaming-enabled');
  const records = [...fixture.records];
  const terminalIndex = records.findLastIndex(record => envelope(record).op === 'PARAGRAPH');
  const appendIndex = records.findIndex(record => envelope(record).op === 'PARAGRAPH_APPEND_OUTPUT');
  assert.ok(appendIndex >= 0 && appendIndex < terminalIndex, 'fixture needs an APPEND before the terminal PARAGRAPH');
  const [append] = records.splice(appendIndex, 1);
  records.push(append);
  const variant = resequence({ ...fixture, records });

  const opOrder = recordList => recordList.map(record => envelope(record).op);
  assert.notDeepEqual(opOrder(variant.records), opOrder(fixture.records));
  assert.deepEqual(validateFixture(variant), []);
  const received = await replay(variant);
  const delayed = envelope(append).data.data;
  const deliveredAt = received.findIndex(
    message => message.op === 'PARAGRAPH_APPEND_OUTPUT' && message.data.data === delayed
  );
  const terminalAt = received.findLastIndex(message => message.op === 'PARAGRAPH');
  assert.equal(received[terminalAt].data.paragraph.status, 'FINISHED');
  assert.ok(terminalAt >= 0 && deliveredAt > terminalAt, 'moved APPEND must be delivered after the terminal PARAGRAPH');
  assert.equal(deliveredAt, received.length - 1);
});
