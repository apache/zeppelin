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

import { AuthInfo, ConfigurationsInfo, ErrorInfo } from './message-common.interface';
import {
  CheckpointNote,
  CloneNote,
  CollaborativeModeStatus,
  DeleteNote,
  EditorSettingReceived,
  EditorSettingSend,
  FolderRename,
  GetInterpreterBindings,
  GetNode,
  ListRevision,
  ListRevisionHistory,
  MoveFolderToTrash,
  MoveNoteToTrash,
  NewNote,
  Note,
  NotesInfo,
  NoteRename,
  NoteRevision,
  NoteRevisionForCompare,
  NoteRunningStatus,
  NoteUpdate,
  NoteUpdated,
  ParagraphAdded,
  ParagraphMoved,
  RemoveFolder,
  RemoveNoteForms,
  RestoreFolder,
  RestoreNote,
  SaveNoteFormsReceived,
  SaveNoteFormsSend,
  SetNoteRevision,
  SetNoteRevisionStatus,
  UpdateParagraph,
  UpdatePersonalizedMode
} from './message-notebook.interface';
import {
  AngularObjectClientBind,
  AngularObjectClientUnbind,
  AngularObjectRemove,
  AngularObjectUpdate,
  AngularObjectUpdated,
  CancelParagraph,
  CommitParagraph,
  Completion,
  CompletionReceived,
  CopyParagraph,
  InsertParagraph,
  MoveParagraph,
  ParagraphClearAllOutput,
  ParagraphClearOutput,
  ParagraphRemove,
  ParagraphRemoved,
  ParasInfo,
  PatchParagraphReceived,
  PatchParagraphSend,
  Progress,
  RunAllParagraphs,
  RunParagraph
} from './message-paragraph.interface';

import { ListNoteJobs, ListUpdateNoteJobs } from './message-job.interface';

import { InterpreterBindings, InterpreterSetting } from './message-interpreter.interface';
import { OP } from './message-operator.interface';

export type MixMessageDataTypeMap = MessageSendDataTypeMap & MessageReceiveDataTypeMap;

export interface MessageReceiveDataTypeMap {
  [OP.COMPLETION_LIST]: CompletionReceived;
  [OP.NOTES_INFO]: NotesInfo;
  [OP.CONFIGURATIONS_INFO]: ConfigurationsInfo;
  [OP.NOTE]: Note;
  [OP.NOTE_REVISION]: NoteRevision;
  [OP.ERROR_INFO]: ErrorInfo;
  [OP.LIST_NOTE_JOBS]: ListNoteJobs;
  [OP.LIST_UPDATE_NOTE_JOBS]: ListUpdateNoteJobs;
  [OP.INTERPRETER_SETTINGS]: InterpreterSetting;
  [OP.LIST_REVISION_HISTORY]: ListRevision;
  [OP.INTERPRETER_BINDINGS]: InterpreterBindings;
  [OP.COLLABORATIVE_MODE_STATUS]: CollaborativeModeStatus;
  [OP.SET_NOTE_REVISION]: SetNoteRevisionStatus;
  [OP.PARAGRAPH_ADDED]: ParagraphAdded;
  [OP.NOTE_RUNNING_STATUS]: NoteRunningStatus;
  [OP.NEW_NOTE]: NoteRevision;
  [OP.SAVE_NOTE_FORMS]: SaveNoteFormsSend;
  [OP.PARAGRAPH]: UpdateParagraph;
  [OP.PATCH_PARAGRAPH]: PatchParagraphSend;
  [OP.PARAGRAPH_REMOVED]: ParagraphRemoved;
  [OP.EDITOR_SETTING]: EditorSettingReceived;
  [OP.PROGRESS]: Progress;
  [OP.PARAGRAPH_MOVED]: ParagraphMoved;
  [OP.AUTH_INFO]: AuthInfo;
  [OP.NOTE_UPDATED]: NoteUpdated;
  [OP.ANGULAR_OBJECT_UPDATE]: AngularObjectUpdate;
  [OP.ANGULAR_OBJECT_REMOVE]: AngularObjectRemove;
  [OP.PARAS_INFO]: ParasInfo;
}

export interface MessageSendDataTypeMap {
  [OP.PING]: undefined;
  [OP.LIST_CONFIGURATIONS]: undefined;
  [OP.LIST_NOTES]: undefined;
  [OP.GET_HOME_NOTE]: undefined;
  [OP.RESTORE_ALL]: undefined;
  [OP.EMPTY_TRASH]: undefined;
  [OP.RELOAD_NOTES_FROM_REPO]: undefined;
  [OP.GET_NOTE]: GetNode;
  [OP.NEW_NOTE]: NewNote;
  [OP.MOVE_NOTE_TO_TRASH]: MoveNoteToTrash;
  [OP.MOVE_FOLDER_TO_TRASH]: MoveFolderToTrash;
  [OP.RESTORE_NOTE]: RestoreNote;
  [OP.RESTORE_FOLDER]: RestoreFolder;
  [OP.REMOVE_FOLDER]: RemoveFolder;
  [OP.DEL_NOTE]: DeleteNote;
  [OP.CLONE_NOTE]: CloneNote;
  [OP.NOTE_UPDATE]: NoteUpdate;
  [OP.UPDATE_PERSONALIZED_MODE]: UpdatePersonalizedMode;
  [OP.NOTE_RENAME]: NoteRename;
  [OP.FOLDER_RENAME]: FolderRename;
  [OP.MOVE_PARAGRAPH]: MoveParagraph;
  [OP.INSERT_PARAGRAPH]: InsertParagraph;
  [OP.COPY_PARAGRAPH]: CopyParagraph;
  [OP.ANGULAR_OBJECT_UPDATED]: AngularObjectUpdated;
  [OP.ANGULAR_OBJECT_CLIENT_BIND]: AngularObjectClientBind;
  [OP.ANGULAR_OBJECT_CLIENT_UNBIND]: AngularObjectClientUnbind;
  [OP.CANCEL_PARAGRAPH]: CancelParagraph;
  [OP.PARAGRAPH_EXECUTED_BY_SPELL]: {}; // TODO(hsuanxyz)
  [OP.RUN_PARAGRAPH]: RunParagraph;
  [OP.RUN_ALL_PARAGRAPHS]: RunAllParagraphs;
  [OP.PARAGRAPH_REMOVE]: ParagraphRemove;
  [OP.PARAGRAPH_CLEAR_OUTPUT]: ParagraphClearOutput;
  [OP.PARAGRAPH_CLEAR_ALL_OUTPUT]: ParagraphClearAllOutput;
  [OP.COMPLETION]: Completion;
  [OP.COMMIT_PARAGRAPH]: CommitParagraph;
  [OP.PATCH_PARAGRAPH]: PatchParagraphReceived;
  [OP.IMPORT_NOTE]: {}; // TODO(hsuanxyz)
  [OP.CHECKPOINT_NOTE]: CheckpointNote;
  [OP.SET_NOTE_REVISION]: SetNoteRevision;
  [OP.LIST_REVISION_HISTORY]: ListRevisionHistory;
  [OP.NOTE_REVISION]: NoteRevision;
  [OP.NOTE_REVISION_FOR_COMPARE]: NoteRevisionForCompare;
  [OP.EDITOR_SETTING]: EditorSettingSend;
  [OP.LIST_NOTE_JOBS]: undefined;
  [OP.UNSUBSCRIBE_UPDATE_NOTE_JOBS]: undefined;
  [OP.LIST_UPDATE_NOTE_JOBS]: undefined;
  [OP.GET_INTERPRETER_BINDINGS]: GetInterpreterBindings;
  [OP.GET_INTERPRETER_SETTINGS]: undefined;
  [OP.SAVE_NOTE_FORMS]: SaveNoteFormsReceived;
  [OP.REMOVE_NOTE_FORMS]: RemoveNoteForms;
}
