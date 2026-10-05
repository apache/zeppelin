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

import assert from 'node:assert/strict';
import test from 'node:test';
import { CommitParagraphSocketProbe } from '../../models/notebook-save-timing.util.ts';

const commit = msgId =>
  JSON.stringify({ op: 'COMMIT_PARAGRAPH', msgId, data: { id: 'p', noteId: 'n', paragraph: 'draft' } });
const reply = msgId => JSON.stringify({ op: 'PARAGRAPH', msgId, data: { paragraph: { id: 'p', text: 'draft' } } });

for (const { name, configure, droppedRequest } of [
  { name: 'request loss', configure: probe => probe.dropFirstCommitParagraph(), droppedRequest: true },
  { name: 'reply loss', configure: probe => probe.dropFirstCommitParagraphResponse(), droppedRequest: false }
]) {
  test(`commit probe isolates ${name} without changing subsequent payloads`, async () => {
    const probe = new CommitParagraphSocketProbe();
    const sent = [];
    const received = [];
    const server = { send: payload => sent.push(payload) };
    const socket = { send: payload => received.push(payload) };
    configure(probe);
    const dropped = [];
    probe.onDroppedCommit(payload => dropped.push(payload));

    probe.handleClientMessage(server, commit('first'));
    assert.deepEqual(sent, droppedRequest ? [] : [commit('first')]);
    if (!droppedRequest) {
      probe.handleServerMessage(socket, reply('first'));
      assert.deepEqual(received, []);
    }

    probe.handleClientMessage(server, commit('second'));
    probe.handleServerMessage(socket, reply('second'));
    assert.equal(sent.at(-1), commit('second'));
    assert.deepEqual(received, [reply('second')]);
    assert.equal(probe.commitCount(), 2);
    assert.deepEqual(dropped, droppedRequest ? [commit('first')] : []);
    await probe.expectNoForwardedResponse('first', 0);
  });
}
