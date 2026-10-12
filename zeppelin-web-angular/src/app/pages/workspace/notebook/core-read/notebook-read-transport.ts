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

import { HttpErrorResponse } from '@angular/common/http';
import { asyncScheduler, merge, timer, Observable, SchedulerLike, Subscription } from 'rxjs';
import { timeout } from 'rxjs/operators';

import type { NotebookReadRequest } from '@zeppelin/notebook-core';
import { MessageReceiveDataTypeMap, OP, WebSocketMessage } from '@zeppelin/sdk';

import { Permissions } from '@zeppelin/interfaces';

/** How long a read waits for its reply before it ends as `failed`. */
const NOTEBOOK_READ_TIMEOUT_MS = 30000;

type ReadReplyOp = OP.NOTE | OP.NOTE_REVISION | OP.AUTH_INFO | OP.ERROR_INFO;
type ReadReply = WebSocketMessage<MessageReceiveDataTypeMap, ReadReplyOp>;

/** The part of the Shell's socket a read uses. Both send methods return the msgId they sent. */
export interface NotebookReadSocket {
  getNote(noteId: string): string;
  noteRevision(noteId: string, revisionId: string): string;
  receiveEnvelope<K extends keyof MessageReceiveDataTypeMap>(
    op: K
  ): Observable<WebSocketMessage<MessageReceiveDataTypeMap, K>>;
}

export interface NotebookAclSource {
  getPermissions(noteId: string): Observable<Permissions>;
}

export interface NotebookReadTransportOptions {
  timeoutMs?: number;
  scheduler?: SchedulerLike;
}

/**
 * Sends the reads of one Core activation and passes only their own replies to it.
 *
 * A note read listens before it sends, then accepts the first NOTE, NOTE_REVISION, AUTH_INFO or
 * ERROR_INFO that carries its msgId and stops listening. Replies without that msgId (broadcasts,
 * other requests, a reply after the read ended) never reach the Core. The permissions read keeps
 * the Core's ACL handle it started with, so a response for an earlier activation is rejected
 * whether it succeeds or fails.
 */
export class NotebookReadTransport {
  private readonly timeoutMs: number;
  private readonly scheduler: SchedulerLike;
  private pending = new Subscription();

  constructor(
    private readonly socket: NotebookReadSocket,
    private readonly aclSource: NotebookAclSource,
    { timeoutMs = NOTEBOOK_READ_TIMEOUT_MS, scheduler = asyncScheduler }: NotebookReadTransportOptions = {}
  ) {
    this.timeoutMs = timeoutMs;
    this.scheduler = scheduler;
  }

  /** Reads the note and its permissions for the request. Ends any reads still pending. Both reads are bounded. */
  load(request: NotebookReadRequest): void {
    this.cancel();
    this.readNote(request);
    this.readAcl(request);
  }

  /** Ends every pending read. Their replies are ignored from now on. */
  cancel(): void {
    this.pending.unsubscribe();
    this.pending = new Subscription();
  }

  private readNote(request: NotebookReadRequest): void {
    const { target } = request;
    const read = new Subscription();
    this.pending.add(read);
    let msgId: string | null = null;
    const early: ReadReply[] = [];

    const settle = (reply: ReadReply) => {
      if (msgId === null) {
        early.push(reply);
        return;
      }
      if (reply.msgId !== msgId || read.closed) {
        return;
      }
      read.unsubscribe();
      this.apply(request, reply);
    };

    read.add(
      merge(
        this.socket.receiveEnvelope(target.kind === 'note' ? OP.NOTE : OP.NOTE_REVISION),
        this.socket.receiveEnvelope(OP.AUTH_INFO),
        this.socket.receiveEnvelope(OP.ERROR_INFO)
      ).subscribe(settle)
    );
    msgId =
      target.kind === 'note'
        ? this.socket.getNote(target.noteId)
        : this.socket.noteRevision(target.noteId, target.revisionId);
    early.splice(0).forEach(settle);
    if (read.closed) {
      return;
    }
    read.add(
      timer(this.timeoutMs, this.scheduler).subscribe(() => {
        read.unsubscribe();
        request.fail('failed');
      })
    );
  }

  private apply(request: NotebookReadRequest, reply: ReadReply): void {
    switch (reply.op) {
      case OP.NOTE: {
        const { note } = (reply.data ?? {}) as MessageReceiveDataTypeMap[OP.NOTE];
        if (note) {
          request.acceptNote(note);
        } else {
          request.fail('failed');
        }
        return;
      }
      case OP.NOTE_REVISION:
        if (reply.data) {
          request.acceptRevision(reply.data as MessageReceiveDataTypeMap[OP.NOTE_REVISION]);
        } else {
          request.fail('failed');
        }
        return;
      case OP.AUTH_INFO:
        request.fail('access-denied');
        return;
      default:
        // The server reports a missing note only as ERROR_INFO text, which is not evidence enough
        // to tell it apart from other failures.
        request.fail('failed');
    }
  }

  private readAcl(request: NotebookReadRequest): void {
    const acl = request.readAcl();
    this.pending.add(
      this.aclSource
        .getPermissions(request.target.noteId)
        .pipe(timeout({ first: this.timeoutMs, scheduler: this.scheduler }))
        .subscribe({
          next: permissions => acl.accept(permissions),
          error: (error: unknown) =>
            acl.fail(error instanceof HttpErrorResponse && error.status === 403 ? 'access-denied' : 'failed')
        })
    );
  }
}
