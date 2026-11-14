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

import { interval, Observable, Observer, Subject, Subscription } from 'rxjs';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';

import { Ticket } from './interfaces/message-common.interface';
import {
  MessageDataTypeMap,
  MessageReceiveDataTypeMap,
  MessageSendDataTypeMap
} from './interfaces/message-data-type-map.interface';
import { WebSocketMessage } from './interfaces/websocket-message.interface';

interface ConnectionIdentity {
  readonly url: string;
  readonly principal: string | undefined;
}

interface ConnectionAttempt {
  identity: ConnectionIdentity;
  socket: WebSocket | null;
  subject: WebSocketSubject<WebSocketMessage<MessageDataTypeMap>> | null;
  subscription: Subscription;
  retired: boolean;
  opened: boolean;
}

interface MessageConnectionHandlers {
  received: Observer<WebSocketMessage<MessageReceiveDataTypeMap>>;
  nextMessageId(): string;
  interceptReceived(message: WebSocketMessage<MessageReceiveDataTypeMap>): WebSocketMessage<MessageReceiveDataTypeMap>;
  ping(): void;
}

/**
 * SDK-internal owner of the physical connection and its pending outbound commands.
 * Requested user/endpoint identity is snapshotted separately from mutable configuration.
 * Changing that identity cancels old unsent work and waits for physical close before reconnecting.
 */
export class MessageConnection {
  public connectedStatus = false;
  public connectedStatus$ = new Subject<boolean>();
  private ws: WebSocketSubject<WebSocketMessage<MessageDataTypeMap>> | null = null;
  private attempt: ConnectionAttempt | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  // Retry after 1, 2, 4, 8, 16, then at most 30 seconds; a valid open resets to 1 second.
  private retryDelay = 1000;
  private online = true;
  private connectionRequested = false;
  private identity: ConnectionIdentity | null = null;
  private destroyed = false;
  private open$ = new Subject<Event>();
  private close$ = new Subject<CloseEvent>();
  private sent$ = new Subject<WebSocketMessage<MessageSendDataTypeMap>>();
  private pendingMessages: Array<WebSocketMessage<MessageSendDataTypeMap>> = [];
  private pingIntervalSubscription = new Subscription();
  private wsUrl?: string;
  private ticket?: Ticket;
  private readonly normalCloseCode = 1000;

  constructor(private readonly handlers: MessageConnectionHandlers) {}

  setOnline(online: boolean): void {
    if (this.destroyed || this.online === online) {
      return;
    }
    this.online = online;
    if (online) {
      this.startConnection();
    } else {
      this.cancelRetry();
      if (this.attempt) {
        this.retireAttempt(this.attempt);
      }
      this.disconnect();
    }
  }

  bootstrap(ticket: Ticket, wsUrl: string): void {
    if (this.destroyed) {
      return;
    }
    this.setTicket(ticket);
    this.setWsUrl(wsUrl);
    this.connect();
  }

  getWsInstance(): WebSocketSubject<WebSocketMessage<MessageDataTypeMap>> | null {
    return this.ws;
  }

  setWsUrl(wsUrl: string): void {
    this.wsUrl = wsUrl;
  }

  setTicket(ticket: Ticket): void {
    this.ticket = ticket;
  }

  connect(): void {
    if (this.destroyed) {
      return;
    }
    if (!this.wsUrl) {
      throw new Error('WebSocket URL is not set. Please call setWsUrl() before connect()');
    }
    this.connectionRequested = true;
    this.startConnection();
  }

  close(): void {
    if (this.destroyed) {
      return;
    }
    this.connectionRequested = false;
    this.identity = null;
    this.cancelRetry();
    this.pendingMessages.length = 0;
    if (this.attempt) {
      this.retireAttempt(this.attempt);
    }
    this.disconnect();
  }

  opened(): Observable<Event> {
    return this.open$.asObservable();
  }

  closed(): Observable<CloseEvent> {
    return this.close$.asObservable();
  }

  sent(): Observable<WebSocketMessage<MessageSendDataTypeMap>> {
    return this.sent$.asObservable();
  }

  send<K extends keyof MessageSendDataTypeMap>(op: K, data: MessageSendDataTypeMap[K] | undefined): string {
    if (this.destroyed || (!this.ws && !this.connectionRequested)) {
      throw new Error('WebSocket is not connected. Bootstrap first.');
    }
    this.startConnection();
    if (this.destroyed || !this.connectionRequested) {
      throw new Error('WebSocket is not connected. Bootstrap first.');
    }
    const message = {
      op,
      msgId: this.handlers.nextMessageId(),
      data,
      ...this.ticket
    };
    console.log('Send:', message.op, message.principal);

    const attempt = this.attempt;
    // Reporting is writable; only the current physical attempt determines whether a frame can be sent.
    if (!attempt || !this.isCurrent(attempt) || !attempt.opened || attempt.socket?.readyState !== 1) {
      this.pendingMessages.push(message);
    } else {
      this.flushPendingMessages(attempt);
      attempt.subject!.next(message);
    }
    this.sent$.next(message);
    return message.msgId;
  }

  destroy(): void {
    if (this.destroyed) {
      return;
    }
    this.destroyed = true;
    this.connectionRequested = false;
    this.identity = null;
    this.cancelRetry();
    this.pendingMessages.length = 0;
    if (this.attempt) {
      this.retireAttempt(this.attempt);
    }
    this.disconnect();
    this.connectedStatus$.complete();
    this.open$.complete();
    this.close$.complete();
    this.sent$.complete();
    this.handlers.received.complete();
  }

  private startConnection(): void {
    if (this.destroyed || !this.connectionRequested) {
      return;
    }
    const principal = this.ticket?.principal;
    if (!this.identity) {
      this.identity = { url: this.wsUrl!, principal };
    } else if (this.identity.url !== this.wsUrl || this.identity.principal !== principal) {
      // Bind the new request before notifications can reenter connect(), close(), or destroy().
      this.identity = { url: this.wsUrl!, principal };
      this.cancelRetry();
      this.pendingMessages.length = 0;
      if (this.attempt) {
        this.retireAttempt(this.attempt);
      }
      this.disconnect();
    }
    if (this.destroyed || !this.connectionRequested || !this.online || this.attempt || this.retryTimer !== null) {
      return;
    }

    this.startAttempt(this.identity!);
  }

  private startAttempt(identity: ConnectionIdentity): void {
    const attempt: ConnectionAttempt = {
      identity,
      socket: null,
      subject: null,
      subscription: new Subscription(),
      retired: false,
      opened: false
    };
    this.attempt = attempt;
    const onClose = (event: CloseEvent) => this.onSocketClose(attempt, event);
    try {
      const subject = webSocket<WebSocketMessage<MessageDataTypeMap>>({
        url: attempt.identity.url,
        // RxJS resets its socket reference on error, before the native close event.
        // Keep the physical socket until close, even after retiring its callbacks.
        WebSocketCtor: class extends WebSocket {
          constructor(url: string) {
            super(url);
            attempt.socket = this;
            this.addEventListener('close', onClose);
          }
        },
        openObserver: { next: event => this.onSocketOpen(attempt, event) }
      });
      attempt.subject = subject;
      this.ws = subject;
      // A constructor failure can notify error before subscribe() returns.
      // The pre-existing parent subscription also tears down that synchronous case.
      attempt.subscription.add(
        subject.subscribe({
          next: message => {
            if (!this.isCurrent(attempt)) {
              return;
            }
            const received = this.handlers.interceptReceived(message as WebSocketMessage<MessageReceiveDataTypeMap>);
            if (this.isCurrent(attempt)) {
              console.log('Receive:', received.op);
              this.handlers.received.next(received);
            }
          },
          error: () => this.onSocketError(attempt)
        })
      );
    } catch {
      this.onSocketError(attempt);
    }
  }

  private isCurrent(attempt: ConnectionAttempt): boolean {
    return (
      this.attempt === attempt &&
      attempt.identity === this.identity &&
      !attempt.retired &&
      !this.destroyed &&
      this.online &&
      this.connectionRequested
    );
  }

  private onSocketOpen(attempt: ConnectionAttempt, event: Event): void {
    if (!this.isCurrent(attempt) || attempt.opened) {
      return;
    }
    this.startConnection();
    if (!this.isCurrent(attempt)) {
      return;
    }
    attempt.opened = true;
    this.retryDelay = 1000;
    this.flushPendingMessages(attempt);
    this.pingIntervalSubscription.unsubscribe();
    this.pingIntervalSubscription = interval(1000 * 10).subscribe(this.handlers.ping);
    this.connectedStatus = true;
    this.connectedStatus$.next(true);
    if (this.isCurrent(attempt)) {
      this.open$.next(event);
    }
  }

  private flushPendingMessages(attempt: ConnectionAttempt): void {
    if (this.pendingMessages.length === 0) {
      return;
    }
    const pending = this.pendingMessages;
    this.pendingMessages = [];
    for (const message of pending) {
      attempt.subject!.next({ ...message, ...this.ticket });
    }
  }

  private onSocketError(attempt: ConnectionAttempt): void {
    if (!this.isCurrent(attempt)) {
      return;
    }
    this.scheduleRetry();
    this.retireAttempt(attempt);
    this.disconnect();
  }

  private onSocketClose(attempt: ConnectionAttempt, event: CloseEvent): void {
    if (this.attempt !== attempt) {
      return;
    }
    const current = this.isCurrent(attempt);
    this.retireAttempt(attempt);
    if (!current) {
      // Offline/close/destroy retired application callbacks, not this close barrier.
      // An online transition or explicit connect may now resume the saved intent.
      this.startConnection();
      return;
    }
    if (event.code === this.normalCloseCode) {
      this.connectionRequested = false;
      this.identity = null;
      this.pendingMessages.length = 0;
    } else {
      this.scheduleRetry();
    }
    this.disconnect();
    if (!this.destroyed) {
      this.close$.next(event);
    }
  }

  private retireAttempt(attempt: ConnectionAttempt): void {
    attempt.retired = true;
    if (this.ws === attempt.subject) {
      this.ws = null;
    }
    attempt.subscription.unsubscribe();
    attempt.subject?.unsubscribe();
    const socket = attempt.socket;
    if (!socket || socket.readyState === 3) {
      if (this.attempt === attempt) {
        this.attempt = null;
      }
    } else if (socket.readyState === 0 || socket.readyState === 1) {
      socket.close();
    }
  }

  private disconnect(): void {
    this.pingIntervalSubscription.unsubscribe();
    this.connectedStatus = false;
    this.connectedStatus$.next(false);
  }

  private scheduleRetry(): void {
    if (this.destroyed || !this.connectionRequested || !this.online || this.retryTimer !== null) {
      return;
    }
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.startConnection();
    }, this.retryDelay);
    this.retryDelay = Math.min(this.retryDelay * 2, 30000);
  }

  private cancelRetry(): void {
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }
}
