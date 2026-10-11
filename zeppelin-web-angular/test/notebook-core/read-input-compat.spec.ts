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

import { assertType, describe, it } from 'vitest';

import type {
  NotebookAclInput,
  NotebookNoteInput,
  NotebookRevisionInput
} from '../../projects/zeppelin-notebook-core/src/public-api';
import type { Note, NoteRevision } from '../../projects/zeppelin-sdk/src/interfaces/message-notebook.interface';
import type { Permissions } from '../../src/app/interfaces/security';

// The Core cannot import SDK types, so its read inputs mirror the wire shapes. If an SDK payload
// type drifts from them, the host could no longer pass it through unchanged and tsc fails here.
describe('notebook core read inputs', () => {
  it('accept the SDK NOTE, NOTE_REVISION and note permissions payloads unchanged', () => {
    assertType<(note: NonNullable<Note['note']>) => NotebookNoteInput>(note => note);
    assertType<(revision: NoteRevision) => NotebookRevisionInput>(revision => revision);
    assertType<(permissions: Permissions) => NotebookAclInput>(permissions => permissions);
  });
});
