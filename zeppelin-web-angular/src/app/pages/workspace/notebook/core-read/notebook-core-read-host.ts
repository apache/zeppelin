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

import { BehaviorSubject, Observable, Subscription } from 'rxjs';

import {
  createNotebookReadStore,
  NotebookCorePort,
  NotebookReadSnapshot,
  NotebookReadStore,
  NotebookReadTarget
} from '@zeppelin/notebook-core';

import {
  NotebookAclSource,
  NotebookReadSocket,
  NotebookReadTransport,
  NotebookReadTransportOptions
} from './notebook-read-transport';

/** The Shell's physical socket state. The host reads it and never connects or retries itself. */
export interface NotebookReadConnection {
  readonly connectedStatus: boolean;
  readonly connectedStatus$: Observable<boolean>;
}

export type NotebookCoreReadState = Readonly<{
  snapshot: NotebookReadSnapshot;
  /**
   * False while the Shell's socket is down. The snapshot is then never a stale `ready`: it is `failed`
   * after a drop, or `loading` while a target waits for the first connection.
   */
  connected: boolean;
}>;

/**
 * Owns the Core of one private read route lifetime: one store, one stable port, and the reads that
 * feed it. The route reuses it across note and revision changes and disposes it on exit.
 */
export class NotebookCoreReadHost {
  /** Stable for the whole lifetime, so a remote can keep it as its NotebookPort. */
  readonly port: NotebookCorePort;
  readonly state$: Observable<NotebookCoreReadState>;

  private readonly store: NotebookReadStore = createNotebookReadStore();
  private readonly transport: NotebookReadTransport;
  private readonly state: BehaviorSubject<NotebookCoreReadState>;
  private readonly subscription = new Subscription();
  private target: NotebookReadTarget | null = null;
  private connected: boolean;
  private disposed = false;

  constructor(
    connection: NotebookReadConnection,
    socket: NotebookReadSocket,
    aclSource: NotebookAclSource,
    options?: NotebookReadTransportOptions
  ) {
    const { store } = this;
    this.port = Object.freeze({
      getSnapshot: () => store.getSnapshot(),
      subscribe: (listener: () => void) => store.subscribe(listener)
    });
    this.transport = new NotebookReadTransport(socket, aclSource, options);
    this.connected = connection.connectedStatus;
    this.state = new BehaviorSubject<NotebookCoreReadState>({
      snapshot: store.getSnapshot(),
      connected: this.connected
    });
    this.state$ = this.state.asObservable();

    this.subscription.add(store.subscribe(() => this.publish()));
    this.subscription.add(connection.connectedStatus$.subscribe(connected => this.onConnection(connected)));
  }

  /** Shows the target, ending the reads of whatever was shown before. */
  open(target: NotebookReadTarget): void {
    if (this.disposed) {
      return;
    }
    this.target = target;
    this.start();
  }

  /** Reads the current target again, e.g. after a failure. */
  retry(): void {
    if (!this.disposed && this.target) {
      this.start();
    }
  }

  /** Ends every read and disposes the Core. Every step is idempotent, so a second call changes nothing. */
  dispose(): void {
    this.disposed = true;
    this.subscription.unsubscribe();
    this.transport.cancel();
    this.store.dispose();
    this.publish();
    this.state.complete();
  }

  private start(): void {
    this.transport.cancel();
    const request = this.store.activate(this.target!);
    if (this.connected) {
      this.transport.load(request);
    }
  }

  private onConnection(connected: boolean): void {
    if (connected === this.connected) {
      return;
    }
    this.connected = connected;
    if (this.target) {
      if (connected) {
        this.start();
      } else {
        // Replies to pending reads are lost with the socket, and a note loaded before the drop may no
        // longer match the server. Neither may survive as ready state.
        this.transport.cancel();
        this.store.activate(this.target).fail('failed');
      }
    }
    this.publish();
  }

  private publish(): void {
    const snapshot = this.store.getSnapshot();
    const current = this.state.getValue();
    if (current.snapshot !== snapshot || current.connected !== this.connected) {
      this.state.next({ snapshot, connected: this.connected });
    }
  }
}
