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
import '@angular/compiler';
import { NotebookCoreRouteAdapter } from '@zeppelin/lifecycle-core-reference';
import { diff_match_patch as DiffMatchPatch } from 'diff-match-patch';

// Only transport-to-adapter bindings live here. The pinned adapter owns all notebook state.
const adapter = new NotebookCoreRouteAdapter({
  getParagraphOutput() {
    throw new Error('This collaboration capture does not execute paragraphs');
  }
});
const events = [];
const patcher = new DiffMatchPatch();
let recoveryRequested = false;

const receive = envelope => {
  const { op, data } = envelope;
  switch (op) {
    case 'NOTE':
      if (adapter.acceptNote(data.note, null) === null) {
        throw new Error('Core rejected the associated NOTE');
      }
      break;
    case 'PATCH_PARAGRAPH':
      if (!adapter.acceptParagraphPatch(data.paragraphId, data.patch)) {
        recoveryRequested = true;
      }
      break;
    case 'NOTE_UPDATED':
      adapter.acceptNoteUpdated(data.name);
      adapter.acceptLookAndFeel(
        ['simple', 'report'].includes(data.config.looknfeel) ? data.config.looknfeel : 'default'
      );
      adapter.acceptPersonalizedMode(data.config.personalizedMode === 'true');
      break;
    case 'COLLABORATIVE_MODE_STATUS':
      adapter.acceptCollaborativeModeStatus(data.status ? data.users : null);
      break;
    default:
      return;
  }
  events.push({ op, snapshot: adapter.port.getSnapshot(), recoveryRequested });
};

// Subscribe before the wire observer: both see the same native client delivery.
const NativeWebSocket = globalThis.window.WebSocket;
globalThis.window.WebSocket = class extends NativeWebSocket {
  constructor(...args) {
    super(...args);
    this.addEventListener('message', event => receive(JSON.parse(String(event.data))));
  }
};

globalThis.window.lifecycleCore = {
  enterContext(context) {
    if (context.state !== 'active' || context.revisionId !== null) {
      throw new Error('The collaboration consumer requires an active live route');
    }
    adapter.enterRoute(context.noteId, context.revisionId);
  },
  beforeSend(envelope) {
    if (envelope.op === 'PATCH_PARAGRAPH') {
      const { id, patch } = envelope.data;
      const paragraph = adapter.port.getSnapshot().paragraphs.find(candidate => candidate.id === id);
      if (!paragraph) {
        throw new Error('Local patch requires a loaded paragraph');
      }

      const [text, applied] = patcher.patch_apply(patcher.patch_fromText(patch), paragraph.text);
      if (!applied.every(Boolean)) {
        throw new Error('Local captured edit cannot be applied');
      }

      adapter.acceptParagraphText(id, text);
    }
  },
  observe() {
    return { snapshot: adapter.port.getSnapshot(), events, recoveryRequested };
  }
};
