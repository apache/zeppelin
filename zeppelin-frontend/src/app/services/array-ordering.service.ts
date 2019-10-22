import { Inject, Injectable } from '@angular/core';
import { TRASH_FOLDER_ID_TOKEN } from '@zeppelin/interfaces';

@Injectable({
  providedIn: 'root'
})
export class ArrayOrderingService {
  noteListOrdering(note) {
    if (note.id === this.TRASH_FOLDER_ID) {
      return '\uFFFF';
    }
    return this.getNoteName(note);
  }

  getNoteName(note) {
    if (note.name === undefined || note.name.trim() === '') {
      return 'Note ' + note.id;
    } else {
      return note.name;
    }
  }

  noteComparator = (v1, v2) => {
    const note1 = v1.value || v1;
    const note2 = v2.value || v2;

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
