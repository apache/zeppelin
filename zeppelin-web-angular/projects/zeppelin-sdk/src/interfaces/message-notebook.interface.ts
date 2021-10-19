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

import { ParagraphItem } from './message-paragraph.interface';

interface ID {
  id: string;
}

interface Name {
  name: string;
}

export type GetNode = ID;
export type MoveNoteToTrash = ID;
export type MoveFolderToTrash = ID;
export type RestoreNote = ID;
export type RestoreFolder = ID;
export type DeleteNote = ID;
export type RemoveFolder = ID;
export type CloneNote = ID & Name;
export type FolderRename = ID & Name;
export type PersonalizedMode = 'true' | 'false';

export interface NoteRename extends Name, ID {
  relative: boolean;
}

export interface SendNote {
  id: string;
  noteParams: NoteParams;
}

export interface NoteUpdated {
  config: NoteConfig;
  info: NoteInfo;
  name: string;
}

export interface Note {
  note?: {
    paragraphs: ParagraphItem[];
    name: string;
    id: string;
    path: string;
    defaultInterpreterGroup: string;
    noteParams: NoteParams;
    noteForms: NoteForms;
    angularObjects: NoteAngularObjects;
    config: NoteConfig;
    info: NoteInfo;
  };
}

export interface NoteAngularObjects {
  // tslint:disable-next-line no-any
  [key: string]: any;
}

export interface NoteInfo {
  // tslint:disable-next-line no-any
  [key: string]: any;
}

export interface NoteParams {
  // tslint:disable-next-line no-any
  [key: string]: any;
}

export interface NoteForms {
  // tslint:disable-next-line no-any
  [key: string]: any;
}

export interface RemoveNoteForms {
  noteId: string;
  formName: string;
}

export interface SaveNoteFormsReceived {
  noteId: string;
  noteParams: NoteParams;
}

export interface GetInterpreterBindings {
  noteId: string;
}

export interface SaveInterpreterBindings {
  noteId: string;
  selectedSettingIds: string[];
}

export interface EditorSettingSend {
  paragraphId: string;
  magic: string;
}

export interface EditorSettingReceived {
  paragraphId: string;
  editor: {
    completionSupport: boolean;
    editOnDblClick: boolean;
    language: string;
  };
}

export interface NoteRevisionForCompare {
  noteId: string;
  revisionId: string;
  position: string;
}

export interface CollaborativeModeStatus {
  status: boolean;
  users: string[];
}

export interface ParagraphMoved {
  index: number;
  id: string;
}

export interface UpdateParagraph {
  paragraph: ParagraphItem;
}

export interface SaveNoteFormsSend {
  formsData: {
    forms: NoteForms;
    params: NoteParams;
  };
}

export interface NoteRunningStatus {
  status: boolean;
}

export interface ParagraphAdded {
  index: number;
  paragraph: ParagraphItem;
}

export interface SetNoteRevisionStatus {
  status: boolean;
}

export interface ListRevision {
  revisionList: RevisionListItem[];
}

export interface RevisionListItem {
  id: string;
  message: string;
  time?: number;
}

export interface NoteRevision {
  note?: Note['note'];
  noteId: string;
  revisionId: string;
}

export interface ListRevisionHistory {
  noteId: string;
}

export interface SetNoteRevision {
  noteId: string;
  revisionId: string;
}

export interface CheckpointNote {
  noteId: string;
  commitMessage: string;
}

export interface NoteUpdate extends Name, ID {
  config: NoteConfig;
}

export interface NewNote extends Name {
  defaultInterpreterGroup: string;
}

export interface NotesInfo {
  notes: NotesInfoItem[];
}

export interface NotesInfoItem extends ID {
  path: string;
}

export interface NoteConfig {
  cron?: string;
  releaseresource: boolean;
  noteFormTitle?: string;
  cronExecutingRoles?: string;
  cronExecutingUser?: string;
  isZeppelinNotebookCronEnable: boolean;
  looknfeel: 'report' | 'default' | 'simple';
  personalizedMode: PersonalizedMode;
}

export interface UpdatePersonalizedMode extends ID {
  personalized: PersonalizedMode;
}
