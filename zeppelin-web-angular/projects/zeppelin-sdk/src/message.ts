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

import { Observable, Subject } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { WebSocketSubject } from 'rxjs/webSocket';

import { Ticket } from './interfaces/message-common.interface';
import {
  MessageDataTypeMap,
  MessageReceiveDataTypeMap,
  MessageSendDataTypeMap
} from './interfaces/message-data-type-map.interface';
import { ImportNote, Note, NoteConfig, PersonalizedMode, SendNote } from './interfaces/message-notebook.interface';
import { OP } from './interfaces/message-operator.interface';
import { ParagraphConfig, ParagraphParams, SendParagraph } from './interfaces/message-paragraph.interface';
import { WebSocketMessage } from './interfaces/websocket-message.interface';

import { MessageConnection } from './message-connection';
import { getMessagePayloadGuard } from './message-payload-guards';

export type ArgumentsType<T> = T extends (...args: infer U) => void ? U : never;

export type SendArgumentsType<K extends keyof MessageSendDataTypeMap> = MessageSendDataTypeMap[K] extends undefined
  ? ArgumentsType<(op: K) => void>
  : ArgumentsType<(op: K, data: MessageSendDataTypeMap[K]) => void>;

export type ReceiveArgumentsType<K extends keyof MessageReceiveDataTypeMap> =
  MessageReceiveDataTypeMap[K] extends undefined ? () => void : (data: MessageReceiveDataTypeMap[K]) => void;

export class Message {
  private received$ = new Subject<WebSocketMessage<MessageReceiveDataTypeMap>>();
  private uniqueClientId = Math.random().toString(36).substring(2, 7);
  // TODO: Clean up this variable with `msgId` in server-side. See ZEPPELIN-6419, ZEPPELIN-4985
  private lastMsgIdSeqSent = 0;
  private readonly connection = new MessageConnection({
    nextMessageId: () => `${this.uniqueClientId}-${++this.lastMsgIdSeqSent}`,
    interceptReceived: message => this.interceptReceived(message),
    received: this.received$,
    ping: () => this.ping()
  });

  get connectedStatus(): boolean {
    return this.connection.connectedStatus;
  }

  get connectedStatus$(): Subject<boolean> {
    return this.connection.connectedStatus$;
  }

  set connectedStatus(connected: boolean) {
    this.connection.connectedStatus = connected;
  }

  set connectedStatus$(status: Subject<boolean>) {
    this.connection.connectedStatus$ = status;
  }

  setOnline(online: boolean): void {
    this.connection.setOnline(online);
  }

  bootstrap(ticket: Ticket, wsUrl: string) {
    this.connection.bootstrap(ticket, wsUrl);
  }

  getWsInstance(): WebSocketSubject<WebSocketMessage<MessageDataTypeMap>> | null {
    return this.connection.getWsInstance();
  }

  setWsUrl(wsUrl: string): void {
    this.connection.setWsUrl(wsUrl);
  }

  setTicket(ticket: Ticket): void {
    this.connection.setTicket(ticket);
  }

  interceptReceived(data: WebSocketMessage<MessageReceiveDataTypeMap>): WebSocketMessage<MessageReceiveDataTypeMap> {
    return data;
  }

  connect() {
    this.connection.connect();
  }

  ping() {
    this.send<OP.PING>(OP.PING);
  }

  close() {
    this.connection.close();
  }

  opened(): Observable<Event> {
    return this.connection.opened();
  }

  closed(): Observable<CloseEvent> {
    return this.connection.closed();
  }

  sent(): Observable<WebSocketMessage<MessageSendDataTypeMap>> {
    return this.connection.sent();
  }

  received(): Observable<WebSocketMessage<MessageReceiveDataTypeMap>> {
    return this.received$.asObservable();
  }

  send<K extends keyof MessageSendDataTypeMap>(...args: SendArgumentsType<K>): string {
    const [op, data] = args;
    return this.connection.send(op, data);
  }

  receive<K extends keyof MessageReceiveDataTypeMap>(op: K): Observable<Record<K, MessageReceiveDataTypeMap[K]>[K]> {
    return this.receiveMessage(op).pipe(map(message => message.data)) as Observable<
      Record<K, MessageReceiveDataTypeMap[K]>[K]
    >;
  }

  receiveEnvelope<K extends keyof MessageReceiveDataTypeMap>(
    op: K
  ): Observable<WebSocketMessage<MessageReceiveDataTypeMap, K>> {
    return this.receiveMessage(op) as Observable<WebSocketMessage<MessageReceiveDataTypeMap, K>>;
  }

  shortCircuit(message: WebSocketMessage<MessageReceiveDataTypeMap>) {
    this.received$.next(this.interceptReceived(message));
  }

  destroy(): void {
    this.connection.destroy();
  }

  getHomeNote(): void {
    this.send<OP.GET_HOME_NOTE>(OP.GET_HOME_NOTE);
  }

  newNote(noteName: string, defaultInterpreterGroup?: string): void {
    this.send<OP.NEW_NOTE>(OP.NEW_NOTE, {
      name: noteName,
      defaultInterpreterGroup
    });
  }

  moveNoteToTrash(noteId: string): void {
    this.send<OP.MOVE_NOTE_TO_TRASH>(OP.MOVE_NOTE_TO_TRASH, {
      id: noteId
    });
  }

  restoreNote(noteId: string): void {
    this.send<OP.RESTORE_NOTE>(OP.RESTORE_NOTE, {
      id: noteId
    });
  }

  deleteNote(noteId: string): void {
    this.send<OP.DEL_NOTE>(OP.DEL_NOTE, {
      id: noteId
    });
  }

  restoreFolder(folderPath: string): void {
    this.send<OP.RESTORE_FOLDER>(OP.RESTORE_FOLDER, {
      id: folderPath
    });
  }

  removeFolder(folderPath: string): void {
    this.send<OP.REMOVE_FOLDER>(OP.REMOVE_FOLDER, {
      id: folderPath
    });
  }

  moveFolderToTrash(folderPath: string): void {
    this.send<OP.MOVE_FOLDER_TO_TRASH>(OP.MOVE_FOLDER_TO_TRASH, {
      id: folderPath
    });
  }

  restoreAll(): void {
    this.send<OP.RESTORE_ALL>(OP.RESTORE_ALL);
  }

  emptyTrash(): void {
    this.send<OP.EMPTY_TRASH>(OP.EMPTY_TRASH);
  }

  cloneNote(noteIdToClone: string, newNoteName: string): void {
    this.send<OP.CLONE_NOTE>(OP.CLONE_NOTE, { id: noteIdToClone, name: newNoteName });
  }

  convertNote(noteId: string, noteName: string): void {
    this.send<OP.CONVERT_NOTE_NBFORMAT>(OP.CONVERT_NOTE_NBFORMAT, { noteId, noteName });
  }

  /**
   * get nodes list
   */
  listNodes(): void {
    this.send<OP.LIST_NOTES>(OP.LIST_NOTES);
  }

  reloadAllNotesFromRepo(): void {
    this.send<OP.RELOAD_NOTES_FROM_REPO>(OP.RELOAD_NOTES_FROM_REPO);
  }

  reloadNote(noteId: string): void {
    this.send<OP.RELOAD_NOTE>(OP.RELOAD_NOTE, { id: noteId });
  }

  getNote(noteId: string): string {
    return this.send<OP.GET_NOTE>(OP.GET_NOTE, { id: noteId });
  }

  updateNote(noteId: string, noteName: string, noteConfig: NoteConfig): void {
    this.send<OP.NOTE_UPDATE>(OP.NOTE_UPDATE, { id: noteId, name: noteName, config: noteConfig });
  }

  updatePersonalizedMode(noteId: string, modeValue: PersonalizedMode): void {
    this.send<OP.UPDATE_PERSONALIZED_MODE>(OP.UPDATE_PERSONALIZED_MODE, { id: noteId, personalized: modeValue });
  }

  noteRename(noteId: string, noteName: string, relative?: boolean): void {
    this.send<OP.NOTE_RENAME>(OP.NOTE_RENAME, { id: noteId, name: noteName, relative });
  }

  folderRename(folderId: string, folderPath: string): void {
    this.send<OP.FOLDER_RENAME>(OP.FOLDER_RENAME, { id: folderId, name: folderPath });
  }

  moveParagraph(paragraphId: string, newIndex: number): void {
    this.send<OP.MOVE_PARAGRAPH>(OP.MOVE_PARAGRAPH, { id: paragraphId, index: newIndex });
  }

  insertParagraph(newIndex: number): string {
    return this.send<OP.INSERT_PARAGRAPH>(OP.INSERT_PARAGRAPH, { index: newIndex });
  }

  copyParagraph(
    newIndex: number,
    paragraphTitle: string | undefined,
    paragraphData: string,
    paragraphConfig: ParagraphConfig,
    paragraphParams: ParagraphParams
  ): string {
    return this.send<OP.COPY_PARAGRAPH>(OP.COPY_PARAGRAPH, {
      index: newIndex,
      title: paragraphTitle,
      paragraph: paragraphData,
      config: paragraphConfig,
      params: paragraphParams
    });
  }

  angularObjectUpdate(
    noteId: string,
    paragraphId: string | null | undefined,
    name: string,
    value: unknown,
    interpreterGroupId: string
  ): void {
    this.send<OP.ANGULAR_OBJECT_UPDATED>(OP.ANGULAR_OBJECT_UPDATED, {
      noteId,
      paragraphId,
      name,
      value,
      interpreterGroupId
    });
  }

  angularObjectClientBind(noteId: string, name: string, value: unknown, paragraphId: string): void {
    this.send<OP.ANGULAR_OBJECT_CLIENT_BIND>(OP.ANGULAR_OBJECT_CLIENT_BIND, {
      noteId,
      name,
      value,
      paragraphId
    });
  }

  angularObjectClientUnbind(noteId: string, name: string, paragraphId: string): void {
    this.send<OP.ANGULAR_OBJECT_CLIENT_UNBIND>(OP.ANGULAR_OBJECT_CLIENT_UNBIND, {
      noteId,
      name,
      paragraphId
    });
  }

  cancelParagraph(paragraphId: string): void {
    this.send<OP.CANCEL_PARAGRAPH>(OP.CANCEL_PARAGRAPH, { id: paragraphId });
  }

  runParagraph(
    paragraphId: string,
    paragraphTitle: string | undefined,
    paragraphData: string,
    paragraphConfig: ParagraphConfig,
    paragraphParams: ParagraphParams
  ): void {
    // short circuit update status without waiting for server response
    this.shortCircuit({
      op: OP.PARAGRAPH_STATUS,
      data: {
        id: paragraphId,
        status: 'PENDING'
      }
    });

    // send message to server
    this.send<OP.RUN_PARAGRAPH>(OP.RUN_PARAGRAPH, {
      id: paragraphId,
      title: paragraphTitle,
      paragraph: paragraphData,
      config: paragraphConfig,
      params: paragraphParams
    });
  }

  runAllParagraphs(noteId: string, paragraphs: SendParagraph[]): void {
    this.send<OP.RUN_ALL_PARAGRAPHS>(OP.RUN_ALL_PARAGRAPHS, {
      noteId,
      paragraphs: JSON.stringify(paragraphs)
    });
  }

  cancelAllParagraphs(noteId: string): void {
    this.send<OP.CANCEL_ALL_PARAGRAPHS>(OP.CANCEL_ALL_PARAGRAPHS, { noteId });
  }

  paragraphRemove(paragraphId: string): void {
    this.send<OP.PARAGRAPH_REMOVE>(OP.PARAGRAPH_REMOVE, { id: paragraphId });
  }

  paragraphClearOutput(paragraphId: string): void {
    this.send<OP.PARAGRAPH_CLEAR_OUTPUT>(OP.PARAGRAPH_CLEAR_OUTPUT, { id: paragraphId });
  }

  paragraphClearAllOutput(noteId: string): void {
    this.send<OP.PARAGRAPH_CLEAR_ALL_OUTPUT>(OP.PARAGRAPH_CLEAR_ALL_OUTPUT, { id: noteId });
  }

  completion(paragraphId: string, buf: string, cursor: number): void {
    this.send<OP.COMPLETION>(OP.COMPLETION, {
      id: paragraphId,
      buf,
      cursor
    });
  }

  commitParagraph(
    paragraphId: string,
    paragraphTitle: string | undefined,
    paragraphData: string,
    paragraphConfig: ParagraphConfig,
    paragraphParams: ParagraphConfig,
    noteId: string
  ): string {
    return this.send<OP.COMMIT_PARAGRAPH>(OP.COMMIT_PARAGRAPH, {
      id: paragraphId,
      noteId,
      title: paragraphTitle,
      paragraph: paragraphData,
      config: paragraphConfig,
      params: paragraphParams
    });
  }

  patchParagraph(paragraphId: string, noteId: string, patch: string): void {
    // javascript add "," if change contains several patches
    // but java library requires patch list without ","
    const normalPatch = patch.replace(/,@@/g, '@@');
    this.send<OP.PATCH_PARAGRAPH>(OP.PATCH_PARAGRAPH, {
      id: paragraphId,
      noteId,
      patch: normalPatch
    });
  }

  importNote(note: ImportNote['note']): void {
    this.send<OP.IMPORT_NOTE>(OP.IMPORT_NOTE, {
      note
    });
  }

  checkpointNote(noteId: string, commitMessage: string): void {
    this.send<OP.CHECKPOINT_NOTE>(OP.CHECKPOINT_NOTE, {
      noteId,
      commitMessage
    });
  }

  setNoteRevision(noteId: string, revisionId: string): void {
    this.send<OP.SET_NOTE_REVISION>(OP.SET_NOTE_REVISION, {
      noteId,
      revisionId
    });
  }

  listRevisionHistory(noteId: string): void {
    this.send<OP.LIST_REVISION_HISTORY>(OP.LIST_REVISION_HISTORY, {
      noteId
    });
  }

  noteRevision(noteId: string, revisionId: string): string {
    return this.send<OP.NOTE_REVISION>(OP.NOTE_REVISION, {
      noteId,
      revisionId
    });
  }

  noteRevisionForCompare(noteId: string, revisionId: string, position: string): void {
    this.send<OP.NOTE_REVISION_FOR_COMPARE>(OP.NOTE_REVISION_FOR_COMPARE, {
      noteId,
      revisionId,
      position
    });
  }

  editorSetting(paragraphId: string, paragraphText: string): void {
    this.send<OP.EDITOR_SETTING>(OP.EDITOR_SETTING, {
      paragraphId,
      paragraphText
    });
  }

  listNoteJobs(): void {
    this.send<OP.LIST_NOTE_JOBS>(OP.LIST_NOTE_JOBS);
  }

  unsubscribeUpdateNoteJobs(): void {
    this.send<OP.UNSUBSCRIBE_UPDATE_NOTE_JOBS>(OP.UNSUBSCRIBE_UPDATE_NOTE_JOBS);
  }

  getInterpreterBindings(noteId: string): void {
    this.send<OP.GET_INTERPRETER_BINDINGS>(OP.GET_INTERPRETER_BINDINGS, { noteId });
  }

  saveInterpreterBindings(noteId: string, selectedSettingIds: string[]): void {
    this.send<OP.SAVE_INTERPRETER_BINDINGS>(OP.SAVE_INTERPRETER_BINDINGS, {
      noteId,
      selectedSettingIds
    });
  }

  getInterpreterSettings(): void {
    this.send<OP.GET_INTERPRETER_SETTINGS>(OP.GET_INTERPRETER_SETTINGS);
  }

  saveNoteForms(note: SendNote): void {
    this.send<OP.SAVE_NOTE_FORMS>(OP.SAVE_NOTE_FORMS, {
      noteId: note.id,
      noteParams: note.noteParams
    });
  }

  removeNoteForms(note: Required<Note>['note'], formName: string): void {
    this.send<OP.REMOVE_NOTE_FORMS>(OP.REMOVE_NOTE_FORMS, {
      noteId: note.id,
      formName
    });
  }

  private receiveMessage<K extends keyof MessageReceiveDataTypeMap>(op: K) {
    const guard = getMessagePayloadGuard(op);

    return this.received$.pipe(
      filter(message => message.op === op),
      filter(message => {
        if (!guard || guard(message.data)) {
          return true;
        }

        // The payload can be large and carries note names, so log the OP alone.
        console.warn(`Dropped WebSocket OP ${String(op)}: payload failed validation`);
        return false;
      })
    );
  }
}
