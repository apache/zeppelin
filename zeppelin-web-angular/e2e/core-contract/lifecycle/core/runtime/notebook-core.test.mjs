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
import test from 'node:test';
import { createNotebookCore } from './notebook-core.ts';

const updates = [
  { type: 'paragraph-progressed', progress: 42 },
  { type: 'paragraph-output-updated', index: 0, result: { type: 'TEXT', data: 'updated' } },
  { type: 'paragraph-output-appended', index: 0, data: 'appended' },
  { type: 'paragraph-output-snapshotted', outputSequence: 1, results: [{ type: 'TEXT', data: 'snapshot' }] }
];

for (const update of updates) {
  test(update.type + ' preserves an unsaved draft until the server confirms its text', () => {
    const core = createNotebookCore({ noteId: 'note-a' });
    core.apply({
      type: 'note-loaded',
      noteId: 'note-a',
      revisionId: null,
      title: 'Note',
      paragraphs: [{ id: 'p-1', text: 'saved', status: 'READY' }]
    });
    core.apply({ type: 'paragraph-updated', paragraphId: 'p-1', text: 'draft', source: 'local' });
    const before = core.port.getSnapshot();

    assert.equal(core.apply({ ...update, paragraphId: 'p-1' }), true);
    const after = core.port.getSnapshot();
    assert.notEqual(after, before);
    assert.equal(after.paragraphs[0].text, 'draft');
    assert.equal(after.paragraphs[0].isDirty, true);
    assert.equal(before.paragraphs[0].isDirty, true);

    core.apply({ type: 'paragraph-updated', paragraphId: 'p-1', text: 'draft', source: 'server' });
    assert.equal(core.port.getSnapshot().paragraphs[0].isDirty, false);
  });
}

for (const eventType of ['note-loaded', 'note-forms-updated']) {
  test(eventType + ' preserves JSON form values and isolates immutable snapshots from their inputs', () => {
    const core = createNotebookCore({ noteId: 'note-a' });
    core.apply({ type: 'note-loaded', noteId: 'note-a', revisionId: null, title: 'Note', paragraphs: [] });
    const structured = { selection: [0, false, null, { tags: ['first'] }] };
    const values = { empty: '', zero: 0, disabled: false, absent: null, structured };
    const forms = {
      field: {
        name: 'field',
        hidden: false,
        type: 'Select',
        defaultValue: structured,
        options: [{ value: structured }]
      }
    };
    const expected = globalThis.structuredClone(structured);
    let notifications = 0;
    core.port.subscribe(() => notifications++);
    core.apply({
      type: eventType,
      noteId: 'note-a',
      revisionId: null,
      title: 'Note',
      paragraphs: [],
      noteForms: forms,
      noteParams: values
    });
    const snapshot = core.port.getSnapshot();
    assert.deepEqual(snapshot.noteParams, values);
    const capturedValues = [
      snapshot.noteParams.structured,
      snapshot.noteForms.field.defaultValue,
      snapshot.noteForms.field.options[0].value
    ];

    structured.selection[3].tags.push('external edit');
    values.zero = 42;
    forms.field.options.push({ value: 'external option' });
    assert.equal(snapshot.noteParams.zero, 0);
    assert.equal(snapshot.noteForms.field.options.length, 1);
    for (const value of capturedValues) {
      assert.deepEqual(value, expected);
      assert.notEqual(value, structured);
      assert.equal(Object.isFrozen(value), true);
      assert.equal(Object.isFrozen(value.selection), true);
      assert.equal(Object.isFrozen(value.selection[3]), true);
      assert.equal(Object.isFrozen(value.selection[3].tags), true);
      assert.throws(() => value.selection[3].tags.push('snapshot edit'), TypeError);
      assert.throws(() => {
        value.selection[3].tags = [];
      }, TypeError);
    }
    assert.equal(core.port.getSnapshot(), snapshot);
    assert.equal(notifications, 1);
    assert.equal(Object.isFrozen(structured), false);

    core.apply({ type: 'note-forms-updated', noteForms: forms, noteParams: values });
    assert.notEqual(core.port.getSnapshot(), snapshot);
    assert.deepEqual(core.port.getSnapshot().noteParams, values);
    assert.deepEqual(snapshot.noteParams.structured, expected);
    assert.equal(notifications, 2);
  });
}
