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

import { expect, Page, WebSocketRoute } from '@playwright/test';

// Zeppelin's own WebSocket endpoint; the Angular CLI serves its HMR socket on /ng-cli-ws.
const ZEPPELIN_WS_URL_PATTERN = /\/ws(\?|$)/;

const JOB_MANAGER_OPS = ['LIST_NOTE_JOBS', 'LIST_UPDATE_NOTE_JOBS', 'JOB_MANAGER_DISABLED'];

// Any fixed instant works — the Job Manager only sorts by it, and no test here asserts order.
const FIXED_UNIX_TIME = 1700000000000;

interface ParagraphJobPayload {
  id: string;
  name: string;
  status: string;
}

// Mirrors JobManagerService.NoteJobInfo(Note) as serialized onto the wire.
export interface NoteJobPayload {
  noteId: string;
  noteName: string;
  noteType: string;
  interpreter: string;
  isRunningJob: boolean;
  isRemoved: boolean;
  unixTimeLastRun: number;
  paragraphs: ParagraphJobPayload[];
}

// What NotebookServer#onNoteRemove broadcasts: NoteJobInfo(noteId, isRemoved) with Gson
// dropping the null noteName / noteType / interpreter / paragraphs fields.
interface RemovedNoteJobPayload {
  noteId: string;
  isRunningJob: boolean;
  isRemoved: true;
  unixTimeLastRun: number;
}

type NoteJobsPayload = NoteJobPayload | RemovedNoteJobPayload;

export const buildNoteJob = (noteName: string): NoteJobPayload => ({
  noteId: `noteId_${noteName}`,
  noteName,
  noteType: 'normal',
  interpreter: 'spark',
  isRunningJob: false,
  isRemoved: false,
  unixTimeLastRun: FIXED_UNIX_TIME,
  paragraphs: [{ id: `paragraph_${noteName}`, name: 'p1', status: 'FINISHED' }]
});

// Serves the Job Manager's WebSocket traffic from the test: zeppelin.jobmanager.enable
// defaults to false and the backend list is owner-filtered, so the real feed can neither
// guarantee a populated list nor produce a cross-user removal broadcast. Everything that is
// not Job Manager traffic is forwarded untouched.
export class JobManagerSocketStub {
  private pageSocket: WebSocketRoute | null = null;

  constructor(private readonly initialJobs: NoteJobPayload[]) {}

  async install(page: Page): Promise<void> {
    await page.routeWebSocket(ZEPPELIN_WS_URL_PATTERN, socket => {
      const server = socket.connectToServer();

      socket.onMessage(message => {
        server.send(message);
        if (opOf(message) === 'LIST_NOTE_JOBS') {
          // The Job Manager page just subscribed — this is Zeppelin's socket, not another
          // WebSocket that happens to live under /ws.
          this.pageSocket = socket;
          socket.send(listNoteJobsMessage(this.initialJobs));
        }
      });

      server.onMessage(message => {
        // Drop the backend's Job Manager traffic; the stub owns this page's job list.
        const op = opOf(message);
        if (op && JOB_MANAGER_OPS.includes(op)) {
          return;
        }
        socket.send(message);
      });
    });
  }

  broadcastUpdate(jobs: NoteJobsPayload[]): void {
    if (!this.pageSocket) {
      throw new Error('JobManagerSocketStub: the Job Manager has not subscribed to the WebSocket yet');
    }
    this.pageSocket.send(
      JSON.stringify({
        op: 'LIST_UPDATE_NOTE_JOBS',
        data: { noteRunningJobs: { lastResponseUnixTime: FIXED_UNIX_TIME, jobs } }
      })
    );
  }

  // The field-less stub broadcast when a note is permanently deleted.
  broadcastRemoval(noteId: string): void {
    this.broadcastUpdate([{ noteId, isRunningJob: false, isRemoved: true, unixTimeLastRun: 0 }]);
  }
}

const listNoteJobsMessage = (jobs: NoteJobPayload[]): string =>
  JSON.stringify({
    op: 'LIST_NOTE_JOBS',
    data: { noteJobs: { lastResponseUnixTime: FIXED_UNIX_TIME, jobs } }
  });

const opOf = (message: string | Buffer): string | undefined => {
  try {
    return (JSON.parse(message.toString()) as { op?: string }).op;
  } catch {
    return undefined;
  }
};

export const collectRuntimeErrors = (page: Page): string[] => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error') {
      errors.push(message.text());
    }
  });
  return errors;
};

// The TypeError a removal stub used to raise inside filterJobs, worded per engine: Chromium
// "Cannot read properties of undefined (reading 'match')", Firefox "job.noteName is
// undefined", WebKit "undefined is not an object (evaluating 'job.noteName.match')".
const NOTE_NAME_ACCESS_ERROR = /reading 'match'|noteName is (undefined|null)|noteName\.match/;

export const expectNoNoteNameAccessError = (errors: string[]): void => {
  expect(errors.filter(error => NOTE_NAME_ACCESS_ERROR.test(error))).toEqual([]);
};
