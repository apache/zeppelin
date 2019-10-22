import { Inject, Injectable } from '@angular/core';

import { TRASH_FOLDER_ID_TOKEN } from '@zeppelin/interfaces';
import { Note, ParagraphItem } from '@zeppelin/sdk';

export const ParagraphStatus = {
  READY: 'READY',
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
  FINISHED: 'FINISHED',
  ABORT: 'ABORT',
  ERROR: 'ERROR'
};

@Injectable({
  providedIn: 'root'
})
export class NoteStatusService {
  isParagraphRunning(paragraph: ParagraphItem) {
    if (!paragraph) {
      return false;
    }
    const status = paragraph.status;
    if (!status) {
      return false;
    }

    return status === ParagraphStatus.PENDING || status === ParagraphStatus.RUNNING;
  }

  isTrash(note: Note['note']) {
    // TODO https://github.com/apache/zeppelin/pull/3365/files
    return note.name.split('/')[1] === this.TRASH_FOLDER_ID;
  }

  viewOnly(note: Note['note']): boolean {
    return note.config.looknfeel === 'report';
  }

  isNoteParagraphRunning(note: Note['note']): boolean {
    if (!note) {
      return false;
    } else {
      return note.paragraphs.some(p => this.isParagraphRunning(p));
    }
  }

  isEntireNoteRunning(note: Note['note']): boolean {
    return !!(note.info && note.info.isRunning && note.info.isRunning === true);
  }

  constructor(@Inject(TRASH_FOLDER_ID_TOKEN) public TRASH_FOLDER_ID: string) {}
}
