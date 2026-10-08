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

import type { NotebookReadNote, NotebookReadParagraph, NotebookReadSnapshot } from './read-model';

// Every selector returns either a value the snapshot already holds or a cached one, so an
// external-store consumer sees the same reference until the underlying state changes.

const hasOwn = (object: object, key: string): boolean => Object.prototype.hasOwnProperty.call(object, key);

const orderedParagraphsByNote = new WeakMap<NotebookReadNote, readonly NotebookReadParagraph[]>();
const noParagraphs: readonly NotebookReadParagraph[] = Object.freeze([]);

/** Paragraphs in note order. The array is reused for as long as the note object is unchanged. */
export const selectOrderedParagraphs = (snapshot: NotebookReadSnapshot): readonly NotebookReadParagraph[] => {
  const note = snapshot.note;
  if (note === null) {
    return noParagraphs;
  }
  let paragraphs = orderedParagraphsByNote.get(note);
  if (paragraphs === undefined) {
    paragraphs = Object.freeze(note.paragraphIds.map(paragraphId => note.paragraphsById[paragraphId]));
    orderedParagraphsByNote.set(note, paragraphs);
  }
  return paragraphs;
};

export const selectParagraph = (
  snapshot: NotebookReadSnapshot,
  paragraphId: string
): NotebookReadParagraph | undefined => {
  const note = snapshot.note;
  return note !== null && hasOwn(note.paragraphsById, paragraphId) ? note.paragraphsById[paragraphId] : undefined;
};

/** The persisted `graph` settings of one result, uninterpreted. */
export const selectChartConfig = (
  snapshot: NotebookReadSnapshot,
  paragraphId: string,
  resultIndex: number
): unknown => {
  const chartConfigs = selectParagraph(snapshot, paragraphId)?.chartConfigs;
  const key = String(resultIndex);
  return chartConfigs !== undefined && hasOwn(chartConfigs, key) ? chartConfigs[key] : undefined;
};

export type NotebookAclCapabilities = Readonly<{
  read: boolean;
  run: boolean;
  write: boolean;
  own: boolean;
}>;

// One frozen object per combination keeps the result reference stable across calls.
const capabilityCombinations = new Map<string, NotebookAclCapabilities>();
const capabilityFor = (read: boolean, run: boolean, write: boolean, own: boolean): NotebookAclCapabilities => {
  const key = `${read}${run}${write}${own}`;
  let combination = capabilityCombinations.get(key);
  if (combination === undefined) {
    combination = Object.freeze({ read, run, write, own });
    capabilityCombinations.set(key, combination);
  }
  return combination;
};

// As on the server, an empty list admits everyone.
const isMember = (list: readonly string[], principals: readonly string[]): boolean =>
  list.length === 0 || list.some(entity => principals.includes(entity));

/**
 * What the note permissions grant to the given user and roles, following the server's list rules:
 * owners can write, writers can run, and runners can read. Until the lists are read, nothing is
 * granted. The server's admin role and anonymous mode are not visible here, so this can deny what
 * the server would allow but never allows what the lists deny.
 */
export const selectAclCapabilities = (
  snapshot: NotebookReadSnapshot,
  principals: readonly string[]
): NotebookAclCapabilities => {
  const { acl } = snapshot;
  if (acl.status !== 'ready') {
    return capabilityFor(false, false, false, false);
  }
  const own = isMember(acl.owners, principals);
  const write = own || isMember(acl.writers, principals);
  const run = write || isMember(acl.runners, principals);
  const read = run || isMember(acl.readers, principals);
  return capabilityFor(read, run, write, own);
};
