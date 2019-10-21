export interface ListNoteJobs {
  noteJobs: NoteJobs;
}

export interface ListUpdateNoteJobs {
  noteRunningJobs: NoteJobs;
}

export interface NoteJobs {
  lastResponseUnixTime: number;
  jobs: JobsItem[];
}
export interface JobsItem {
  noteId: string;
  noteName: string;
  noteType: string;
  interpreter: string;
  isRunningJob: boolean;
  isRemoved: boolean;
  unixTimeLastRun: number;
  paragraphs: JobItemParagraphItem[];
}
export interface JobItemParagraphItem {
  id: string;
  name: string;
  status: JobStatus;
}

export enum JobStatus {
  READY = 'READY',
  FINISHED = 'FINISHED',
  ABORT = 'ABORT',
  ERROR = 'ERROR',
  PENDING = 'PENDING',
  RUNNING = 'RUNNING'
}
