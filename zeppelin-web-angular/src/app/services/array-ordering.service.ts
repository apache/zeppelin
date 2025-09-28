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

import { Inject, Injectable } from '@angular/core';
import { NodeItem, TRASH_FOLDER_ID_TOKEN } from '@zeppelin/interfaces';

@Injectable({
  providedIn: 'root'
})
export class ArrayOrderingService {
  noteListOrdering(note: NodeItem) {
    if (note.id === this.TRASH_FOLDER_ID) {
      return '\uFFFF';
    }
    return this.getNoteName(note);
  }

  getNoteName(note: NodeItem) {
    if (note.title.trim() === '') {
      return 'Note ' + note.id;
    } else {
      return note.title;
    }
  }

  noteComparator = (v1: NodeItem, v2: NodeItem) => {
    const note1 = v1;
    const note2 = v2;

    if (note1.id === this.TRASH_FOLDER_ID) {
      return 1;
    }

    if (note2.id === this.TRASH_FOLDER_ID) {
      return -1;
    }

    if (note1.children === undefined && note2.children !== undefined) {
      return 1;
    }

    if (note1.children !== undefined && note2.children === undefined) {
      return -1;
    }

    const noteName1 = this.getNoteName(note1);
    const noteName2 = this.getNoteName(note2);

    return noteName1.localeCompare(noteName2);
  };

  constructor(@Inject(TRASH_FOLDER_ID_TOKEN) private TRASH_FOLDER_ID: string) {}
}
