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

import { interval, Observable, Subject, Subscription } from 'rxjs';
import { delay, filter, map, mergeMap, retryWhen, take } from 'rxjs/operators';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';

import { Ticket } from './interfaces/message-common.interface';
import {
  MessageReceiveDataTypeMap,
  MessageSendDataTypeMap,
  MixMessageDataTypeMap
} from './interfaces/message-data-type-map.interface';
import { NoteConfig, PersonalizedMode, SendNote } from './interfaces/message-notebook.interface';
import { OP } from './interfaces/message-operator.interface';
import { ParagraphConfig, ParagraphParams, SendParagraph } from './interfaces/message-paragraph.interface';
import { WebSocketMessage } from './interfaces/websocket-message.interface';

export type ArgumentsType<T> = T extends (...args: infer U) => void ? U : never;

export type SendArgumentsType<K extends keyof MessageSendDataTypeMap> = MessageSendDataTypeMap[K] extends undefined
  ? ArgumentsType<(op: K) => void>
  : ArgumentsType<(op: K, data: MessageSendDataTypeMap[K]) => void>;

export type ReceiveArgumentsType<
  K extends keyof MessageReceiveDataTypeMap
> = MessageReceiveDataTypeMap[K] extends undefined ? () => void : (data?: MessageReceiveDataTypeMap[K]) => void;

export class Message {
  public connectedStatus = false;
  public connectedStatus$ = new Subject<boolean>();
  private ws: WebSocketSubject<WebSocketMessage<keyof MixMessageDataTypeMap>>;
  private open$ = new Subject<Event>();
  private close$ = new Subject<CloseEvent>();
  private sent$ = new Subject<WebSocketMessage<keyof MessageSendDataTypeMap>>();
  private received$ = new Subject<WebSocketMessage<keyof MessageReceiveDataTypeMap>>();
  private pingIntervalSubscription = new Subscription();
  private wsUrl: string;
  private ticket: Ticket;

  constructor() {
    this.open$.subscribe(() => {
      this.connectedStatus = true;
      this.connectedStatus$.next(this.connectedStatus);
      this.pingIntervalSubscription.unsubscribe();
      this.pingIntervalSubscription = interval(1000 * 10).subscribe(() => this.ping());
    });
    this.close$.subscribe(() => {
      this.connectedStatus = false;
      this.connectedStatus$.next(this.connectedStatus);
      this.pingIntervalSubscription.unsubscribe();
    });
  }

  bootstrap(ticket: Ticket, wsUrl: string) {
    this.setTicket(ticket);
    this.setWsUrl(wsUrl);
    this.connect();
  }

  getWsInstance(): WebSocketSubject<WebSocketMessage<keyof MixMessageDataTypeMap>> {
    return this.ws;
  }

  setWsUrl(wsUrl: string): void {
    this.wsUrl = wsUrl;
  }

  setTicket(ticket: Ticket): void {
    this.ticket = ticket;
  }

  interceptReceived(
    data: WebSocketMessage<keyof MessageReceiveDataTypeMap>
  ): WebSocketMessage<keyof MessageReceiveDataTypeMap> {
    return data;
  }

  connect() {
    this.ws = webSocket({
      url: this.wsUrl,
      openObserver: this.open$,
      closeObserver: this.close$
    });

    this.ws
      .pipe(
        // reconnect
        retryWhen(errors =>
          errors.pipe(
            mergeMap(() =>
              this.close$.pipe(
                take(1),
                delay(4000)
              )
            )
          )
        )
      )
      .subscribe((e: WebSocketMessage<keyof MessageReceiveDataTypeMap>) => {
        console.log('Receive:', e);
        this.received$.next(this.interceptReceived(e));
      });
  }

  ping() {
    this.send<OP.PING>(OP.PING);
  }

  close() {
    this.close$.next();
  }

  opened(): Observable<Event> {
    return this.open$.asObservable();
  }

  closed(): Observable<CloseEvent> {
    return this.close$.asObservable();
  }

  sent(): Observable<WebSocketMessage<keyof MessageSendDataTypeMap>> {
    return this.sent$.asObservable();
  }

  received(): Observable<WebSocketMessage<keyof MessageReceiveDataTypeMap>> {
    return this.received$.asObservable();
  }

  send<K extends keyof MessageSendDataTypeMap>(...args: SendArgumentsType<K>): void {
    const [op, data] = args;
    const message: WebSocketMessage<K> = {
      op,
      data: data as MixMessageDataTypeMap[K],
      ...this.ticket
    };
    console.log('Send:', message);

    this.ws.next(message);
    this.sent$.next(message);
  }

  receive<K extends keyof MessageReceiveDataTypeMap>(op: K): Observable<Record<K, MessageReceiveDataTypeMap[K]>[K]> {
    return this.received$.pipe(
      filter(message => message.op === op),
      map(message => message.data)
    ) as Observable<Record<K, MessageReceiveDataTypeMap[K]>[K]>;
  }

  destroy(): void {
    this.ws.complete();
    this.ws = null;
  }

  getHomeNote(): void {
    this.send<OP.GET_HOME_NOTE>(OP.GET_HOME_NOTE);
  }

  newNote(noteName: string, defaultInterpreterGroup: string): void {
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

  deleteNote(noteId): void {
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

  cloneNote(noteIdToClone, newNoteName): void {
    this.send<OP.CLONE_NOTE>(OP.CLONE_NOTE, { id: noteIdToClone, name: newNoteName });
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

  getNote(noteId: string): void {
    this.send<OP.GET_NOTE>(OP.GET_NOTE, { id: noteId });
  }

  updateNote(noteId: string, noteName: string, noteConfig: NoteConfig): void {
    this.send<OP.NOTE_UPDATE>(OP.NOTE_UPDATE, { id: noteId, name: noteName, config: noteConfig });
  }

  updatePersonalizedMode(noteId: string, modeValue: PersonalizedMode): void {
    this.send<OP.UPDATE_PERSONALIZED_MODE>(OP.UPDATE_PERSONALIZED_MODE, { id: noteId, personalized: modeValue });
  }

  noteRename(noteId: string, noteName: string, relative: boolean): void {
    this.send<OP.NOTE_RENAME>(OP.NOTE_RENAME, { id: noteId, name: noteName, relative: relative });
  }

  folderRename(folderId: string, folderPath: string): void {
    this.send<OP.FOLDER_RENAME>(OP.FOLDER_RENAME, { id: folderId, name: folderPath });
  }

  moveParagraph(paragraphId: string, newIndex: number): void {
    this.send<OP.MOVE_PARAGRAPH>(OP.MOVE_PARAGRAPH, { id: paragraphId, index: newIndex });
  }

  insertParagraph(newIndex: number): void {
    this.send<OP.INSERT_PARAGRAPH>(OP.INSERT_PARAGRAPH, { index: newIndex });
  }

  copyParagraph(
    newIndex: number,
    paragraphTitle: string,
    paragraphData: string,
    paragraphConfig: ParagraphConfig,
    paragraphParams: ParagraphParams
  ): void {
    this.send<OP.COPY_PARAGRAPH>(OP.COPY_PARAGRAPH, {
      index: newIndex,
      title: paragraphTitle,
      paragraph: paragraphData,
      config: paragraphConfig,
      params: paragraphParams
    });
  }

  angularObjectUpdate(
    noteId: string,
    paragraphId: string,
    name: string,
    value: string,
    interpreterGroupId: string
  ): void {
    this.send<OP.ANGULAR_OBJECT_UPDATED>(OP.ANGULAR_OBJECT_UPDATED, {
      noteId: noteId,
      paragraphId: paragraphId,
      name: name,
      value: value,
      interpreterGroupId: interpreterGroupId
    });
  }

  // tslint:disable-next-line:no-any
  angularObjectClientBind(noteId: string, name: string, value: any, paragraphId: string): void {
    this.send<OP.ANGULAR_OBJECT_CLIENT_BIND>(OP.ANGULAR_OBJECT_CLIENT_BIND, {
      noteId: noteId,
      name: name,
      value: value,
      paragraphId: paragraphId
    });
  }

  angularObjectClientUnbind(noteId: string, name: string, paragraphId: string): void {
    this.send<OP.ANGULAR_OBJECT_CLIENT_UNBIND>(OP.ANGULAR_OBJECT_CLIENT_UNBIND, {
      noteId: noteId,
      name: name,
      paragraphId: paragraphId
    });
  }

  cancelParagraph(paragraphId): void {
    this.send<OP.CANCEL_PARAGRAPH>(OP.CANCEL_PARAGRAPH, { id: paragraphId });
  }

  paragraphExecutedBySpell(
    paragraphId,
    paragraphTitle,
    paragraphText,
    paragraphResultsMsg,
    paragraphStatus,
    paragraphErrorMessage,
    paragraphConfig,
    paragraphParams,
    paragraphDateStarted,
    paragraphDateFinished
  ): void {
    this.send<OP.PARAGRAPH_EXECUTED_BY_SPELL>(OP.PARAGRAPH_EXECUTED_BY_SPELL, {
      id: paragraphId,
      title: paragraphTitle,
      paragraph: paragraphText,
      results: {
        code: paragraphStatus,
        msg: paragraphResultsMsg.map(dataWithType => {
          const serializedData = dataWithType.data;
          return { type: dataWithType.type, serializedData };
        })
      },
      status: paragraphStatus,
      errorMessage: paragraphErrorMessage,
      config: paragraphConfig,
      params: paragraphParams,
      dateStarted: paragraphDateStarted,
      dateFinished: paragraphDateFinished
    });
  }

  runParagraph(
    paragraphId: string,
    paragraphTitle: string,
    paragraphData: string,
    paragraphConfig: ParagraphConfig,
    paragraphParams: ParagraphParams
  ): void {
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
      noteId: noteId,
      paragraphs: JSON.stringify(paragraphs)
    });
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
      buf: buf,
      cursor: cursor
    });
  }

  commitParagraph(
    paragraphId: string,
    paragraphTitle: string,
    paragraphData: string,
    paragraphConfig: ParagraphConfig,
    paragraphParams: ParagraphConfig,
    noteId: string
  ): void {
    return this.send<OP.COMMIT_PARAGRAPH>(OP.COMMIT_PARAGRAPH, {
      id: paragraphId,
      noteId: noteId,
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
    return this.send<OP.PATCH_PARAGRAPH>(OP.PATCH_PARAGRAPH, {
      id: paragraphId,
      noteId: noteId,
      patch: normalPatch
    });
  }

  importNote(note: SendNote): void {
    this.send<OP.IMPORT_NOTE>(OP.IMPORT_NOTE, {
      note: note
    });
  }

  checkpointNote(noteId: string, commitMessage: string): void {
    this.send<OP.CHECKPOINT_NOTE>(OP.CHECKPOINT_NOTE, {
      noteId: noteId,
      commitMessage: commitMessage
    });
  }

  setNoteRevision(noteId: string, revisionId: string): void {
    this.send<OP.SET_NOTE_REVISION>(OP.SET_NOTE_REVISION, {
      noteId: noteId,
      revisionId: revisionId
    });
  }

  listRevisionHistory(noteId: string): void {
    this.send<OP.LIST_REVISION_HISTORY>(OP.LIST_REVISION_HISTORY, {
      noteId: noteId
    });
  }

  noteRevision(noteId: string, revisionId: string): void {
    this.send<OP.NOTE_REVISION>(OP.NOTE_REVISION, {
      noteId: noteId,
      revisionId: revisionId
    });
  }

  noteRevisionForCompare(noteId: string, revisionId: string, position: string): void {
    this.send<OP.NOTE_REVISION_FOR_COMPARE>(OP.NOTE_REVISION_FOR_COMPARE, {
      noteId: noteId,
      revisionId: revisionId,
      position: position
    });
  }

  editorSetting(paragraphId: string, replName: string): void {
    this.send<OP.EDITOR_SETTING>(OP.EDITOR_SETTING, {
      paragraphId: paragraphId,
      magic: replName
    });
  }

  listNoteJobs(): void {
    this.send<OP.LIST_NOTE_JOBS>(OP.LIST_NOTE_JOBS);
  }

  unsubscribeUpdateNoteJobs(): void {
    this.send<OP.UNSUBSCRIBE_UPDATE_NOTE_JOBS>(OP.UNSUBSCRIBE_UPDATE_NOTE_JOBS);
  }

  getInterpreterBindings(noteId: string): void {
    this.send<OP.GET_INTERPRETER_BINDINGS>(OP.GET_INTERPRETER_BINDINGS, { noteId: noteId });
  }

  saveInterpreterBindings(noteId, selectedSettingIds): void {
    // this.send<OP.SAVE_INTERPRETER_BINDINGS>(OP.SAVE_INTERPRETER_BINDINGS,
    //  {noteId: noteId, selectedSettingIds: selectedSettingIds});
  }

  listConfigurations(): void {
    this.send<OP.LIST_CONFIGURATIONS>(OP.LIST_CONFIGURATIONS);
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

  removeNoteForms(note, formName): void {
    this.send<OP.REMOVE_NOTE_FORMS>(OP.REMOVE_NOTE_FORMS, {
      noteId: note.id,
      formName: formName
    });
  }
}
