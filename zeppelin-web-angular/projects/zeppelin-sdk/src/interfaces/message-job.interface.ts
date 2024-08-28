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
