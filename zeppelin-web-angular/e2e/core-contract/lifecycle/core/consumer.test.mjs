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
import test from 'node:test';
import { LifecycleCoreConsumer } from './consumer.ts';

const fixture = JSON.parse(
  readFileSync(new URL('../../../fixtures/notebook-lifecycle/collaboration-anonymous.json', import.meta.url))
);
const frame = (op, direction = 'receive') =>
  fixture.records.find(
    record =>
      record.kind === 'websocket' &&
      record.websocket.direction === direction &&
      JSON.parse(record.websocket.payloadText).op === op
  ).websocket.payloadText;
const initialNote = frame('NOTE');
const noteId = JSON.parse(initialNote).data.note.id;
const context = { state: 'active', noteId, revisionId: null };
const loadedConsumer = () => {
  const consumer = new LifecycleCoreConsumer();
  consumer.enterContext(context);
  consumer.receive(initialNote);
  return consumer;
};

test('local drafts and received patches update independent Core instances', () => {
  const sender = loadedConsumer();
  const follower = loadedConsumer();
  const original = follower.observe().snapshot;
  sender.beforeSend(frame('PATCH_PARAGRAPH', 'send'));
  assert.equal(sender.observe().snapshot.paragraphs[0].text, '%md modified');
  assert.equal(sender.observe().snapshot.paragraphs[0].isDirty, true);
  assert.equal(follower.observe().snapshot, original);
  follower.receive(frame('PATCH_PARAGRAPH'));
  assert.equal(follower.observe().snapshot.paragraphs[0].text, '%md modified');
  assert.equal(follower.observe().snapshot.paragraphs[0].isDirty, false);
});

test('a failed received patch preserves state and requires a matching full-note recovery', () => {
  const consumer = loadedConsumer();
  const original = consumer.observe().snapshot;
  const patch = JSON.parse(frame('PATCH_PARAGRAPH'));
  patch.data.patch = 'invalid patch';
  consumer.receive(JSON.stringify(patch));
  assert.equal(consumer.observe().snapshot, original);
  assert.equal(consumer.observe().recoveryRequired, true);

  const wrongNote = JSON.parse(initialNote);
  wrongNote.data.note.id = 'another-note';
  assert.throws(() => consumer.receive(JSON.stringify(wrongNote)), /Core rejected the associated NOTE/);
  assert.equal(consumer.observe().recoveryRequired, true);
  consumer.receive(initialNote);
  assert.equal(consumer.observe().recoveryRequired, false);
});

test('a refetched note replaces a dirty draft with the server-confirmed text', () => {
  const consumer = loadedConsumer();
  consumer.beforeSend(frame('PATCH_PARAGRAPH', 'send'));
  consumer.receive(initialNote);
  const paragraph = consumer.observe().snapshot.paragraphs[0];
  assert.equal(paragraph.text, JSON.parse(initialNote).data.note.paragraphs[0].text);
  assert.equal(paragraph.isDirty, false);
});

test('the consumer rejects invalid contexts and unsatisfied local patch preconditions', () => {
  const consumer = new LifecycleCoreConsumer();
  assert.throws(() => consumer.enterContext({ ...context, state: 'inactive' }), /active live route/);
  assert.throws(() => consumer.enterContext({ ...context, revisionId: 'revision' }), /active live route/);
  consumer.enterContext(context);
  assert.throws(() => consumer.beforeSend(frame('PATCH_PARAGRAPH', 'send')), /Local captured edit cannot be applied/);
  assert.throws(() => consumer.receive(JSON.stringify({ op: 'NOTE', data: {} })), /requires a full NOTE/);
});

test('wire fields reach Core snapshots without losing execution or notebook configuration', () => {
  const consumer = loadedConsumer();
  const message = JSON.parse(initialNote);
  const note = message.data.note;
  note.paragraphs[0].progress = 63;
  note.paragraphs[0].config.editorSetting = { language: 'python' };
  note.paragraphs[0].results = { msg: [{ type: 'TEXT', data: 'captured output' }] };
  note.config.isZeppelinNotebookCronEnable = true;
  note.config.cron = '0 0 * * * ?';
  note.config.releaseresource = true;
  note.config.personalizedMode = 'true';
  consumer.receive(JSON.stringify(message));
  const snapshot = consumer.observe().snapshot;
  assert.equal(snapshot.paragraphs[0].progress, 63);
  assert.equal(snapshot.paragraphs[0].language, 'python');
  assert.deepEqual(snapshot.paragraphs[0].results, note.paragraphs[0].results.msg);
  assert.equal(snapshot.personalizedMode, true);
  assert.deepEqual(snapshot.scheduler, { cron: note.config.cron, releaseResource: true });
  consumer.receive(frame('NOTE_UPDATED'));
  assert.equal(consumer.observe().snapshot.title, JSON.parse(frame('NOTE_UPDATED')).data.name);
  consumer.receive(frame('COLLABORATIVE_MODE_STATUS'));
  assert.deepEqual(
    consumer.observe().snapshot.collaborativeUsers,
    JSON.parse(frame('COLLABORATIVE_MODE_STATUS')).data.users
  );
});
