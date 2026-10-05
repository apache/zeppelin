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

import { beforeEach, describe, expect, it } from 'vitest';
import { NodeItem } from '@zeppelin/interfaces';
// Through the barrel, not the neighbouring file:
// that is what walks the alias chain to monaco and @zeppelin/sdk,
// so this spec fails if either stops resolving.
import { ArrayOrderingService } from '@zeppelin/services';

const TRASH_FOLDER_ID = '~Trash';

const note = (id: string, title: string, children?: NodeItem[]): NodeItem => ({ id, title, children }) as NodeItem;

describe('ArrayOrderingService', () => {
  let service: ArrayOrderingService;

  beforeEach(() => {
    service = new ArrayOrderingService(TRASH_FOLDER_ID);
  });

  describe('getNoteName', () => {
    it('returns the title when it has one', () => {
      expect(service.getNoteName(note('a1', 'My note'))).toBe('My note');
    });

    it('falls back to the id when the title is blank', () => {
      expect(service.getNoteName(note('a1', '   '))).toBe('Note a1');
    });
  });

  describe('noteListOrdering', () => {
    it('sorts the trash folder last by returning the highest code point', () => {
      expect(service.noteListOrdering(note(TRASH_FOLDER_ID, 'Trash'))).toBe('￿');
    });

    it('orders every other node by its display name', () => {
      expect(service.noteListOrdering(note('a1', 'My note'))).toBe('My note');
    });
  });

  describe('noteComparator', () => {
    it('puts the trash folder after anything else, whichever side it is on', () => {
      const trash = note(TRASH_FOLDER_ID, 'Trash');
      const other = note('a1', 'My note');

      expect(service.noteComparator(trash, other)).toBe(1);
      expect(service.noteComparator(other, trash)).toBe(-1);
    });

    it('puts folders before notes', () => {
      const folder = note('f1', 'Folder', []);
      const leaf = note('a1', 'Note');

      expect(service.noteComparator(leaf, folder)).toBe(1);
      expect(service.noteComparator(folder, leaf)).toBe(-1);
    });

    it('compares two nodes of the same kind by display name', () => {
      expect(service.noteComparator(note('a1', 'Alpha'), note('a2', 'Beta'))).toBeLessThan(0);
    });

    it('uses the id fallback when a title is blank', () => {
      // 'Note a1' sorts before 'Zebra', which the raw empty title would not.
      expect(service.noteComparator(note('a1', ''), note('a2', 'Zebra'))).toBeLessThan(0);
    });
  });
});
