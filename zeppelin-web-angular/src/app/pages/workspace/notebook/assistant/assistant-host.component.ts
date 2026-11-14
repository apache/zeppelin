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

import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges
} from '@angular/core';
import { NzMessageService } from 'ng-zorro-antd/message';
import { Subject, takeUntil } from 'rxjs';

import { OP, type AssistantConnection, type AssistantParagraphRef, type AssistantShellProps } from '@zeppelin/sdk';
import { BaseUrlService, MessageService, TicketService } from '@zeppelin/services';
import { AssistantSlots } from './assistant-slots';

@Component({
  selector: 'zeppelin-assistant-host',
  template: `
    @if (enabled && !failed && assistantProps) {
      <div zeppelin-react-mount="./AssistantWorkspace" [reactProps]="assistantProps"></div>
    }
  `,
  standalone: false
})
export class AssistantHostComponent implements OnInit, OnChanges, OnDestroy {
  @Input() enabled = false;
  @Input() noteId!: string;
  @Input() panelWidth!: number;
  @Input() paragraphs: AssistantParagraphRef[] = [];
  @Output() readonly openParagraph = new EventEmitter<string>();
  @Output() readonly panelWidthChange = new EventEmitter<number>();
  assistantProps: AssistantShellProps | null = null;
  failed = false;

  private readonly destroy$ = new Subject<void>();
  private destroyed = false;
  private connection: AssistantConnection | null = null;
  private sessionKey = 0;
  private principal: string | null;
  private loggingOut: boolean;
  private retired = false;
  private awaitingLogin = false;

  constructor(
    private readonly slots: AssistantSlots,
    private readonly cdr: ChangeDetectorRef,
    private readonly nzMessageService: NzMessageService,
    private readonly baseUrlService: BaseUrlService,
    private readonly messageService: MessageService,
    private readonly ticketService: TicketService
  ) {
    this.loggingOut = ticketService.logout$.value;
    this.awaitingLogin = this.loggingOut || !ticketService.ticket.init;
    this.principal = this.loggingOut || !ticketService.ticket.init ? null : ticketService.ticket.principal;
  }

  ngOnInit(): void {
    this.ticketService.logout$.pipe(takeUntil(this.destroy$)).subscribe(loggingOut => {
      if (loggingOut && !this.loggingOut) {
        this.loggingOut = true;
        this.awaitingLogin = true;
        this.principal = null;
        this.changeSession();
      } else if (!loggingOut) {
        this.loggingOut = false;
        this.updatePrincipal(this.ticketService.ticket);
      }
    });
    this.ticketService.ticket$.pipe(takeUntil(this.destroy$)).subscribe(ticket => {
      this.loggingOut = false;
      this.awaitingLogin = !ticket.init;
      this.updatePrincipal(ticket);
    });
    this.slots.slots.pipe(takeUntil(this.destroy$)).subscribe(() => {
      queueMicrotask(() => {
        if (this.destroyed) return;
        this.refreshProps();
      });
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.noteId && !changes.noteId.firstChange && changes.noteId.previousValue !== this.noteId) {
      this.retire();
      this.failed = false;
    }
    if (!this.enabled) {
      this.retire();
      return;
    }
    if (changes.noteId || changes.enabled) this.retired = false;
    if (changes.enabled) this.failed = false;
    this.refreshProps();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.retire();
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Invalidates this note's connection before the next note has loaded. */
  retire(): void {
    this.retired = true;
    this.connection = null;
    this.assistantProps = null;
    this.slots.requestPanelClose();
    this.slots.setPanelOpen(false);
  }

  private updatePrincipal(ticket: { init: boolean; principal: string }): void {
    const principal = ticket.init ? ticket.principal : null;
    if (this.principal === principal) return;
    this.principal = principal;
    this.changeSession();
  }

  private changeSession(): void {
    const retired = this.retired;
    this.sessionKey++;
    this.retire();
    this.retired = retired;
    this.refreshProps();
  }

  private createConnection(): AssistantConnection {
    const current = (): boolean => !this.destroyed && this.enabled && this.connection === connection;
    const connection: AssistantConnection = {
      apiBase: this.baseUrlService.getRestApiBase(),
      sessionKey: this.sessionKey,
      onAuthError: (status, location) => {
        if (current()) this.ticketService.handleAuthFailure(status, location);
      },
      socket: {
        send: message => {
          if (!current() || !this.messageService.connectedStatus) {
            throw new Error('Assistant WebSocket is not connected.');
          }
          this.messageService.send(OP.ASSISTANT_SEND_MESSAGE, message);
        },
        subscribe: listener => {
          const subscription = this.messageService
            .receive(OP.ASSISTANT_EVENT)
            .pipe(takeUntil(this.destroy$))
            .subscribe(event => {
              if (current()) listener(event);
            });
          return () => subscription.unsubscribe();
        },
        subscribeClose: listener => {
          const subscription = this.messageService
            .closed()
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => {
              if (current()) listener();
            });
          return () => subscription.unsubscribe();
        }
      }
    };
    return connection;
  }

  private readonly onOpenParagraph = (paragraphId: string): void => {
    this.openParagraph.emit(paragraphId);
  };

  private readonly onPanelVisibilityChange = (visible: boolean): void => {
    this.slots.setPanelOpen(visible);
  };

  private readonly subscribePanelClose = (listener: () => void): (() => void) => {
    const subscription = this.slots.panelCloseRequests.subscribe(listener);
    return () => subscription.unsubscribe();
  };

  private readonly onPanelWidthChange = (width: number): void => {
    this.panelWidthChange.emit(width);
  };

  private readonly onError = (error: unknown): void => {
    console.error('React assistant error', error);
    // The shell renders into the sidebar slots, so the host has no place of its own for a fallback.
    this.nzMessageService.error('Unable to load the AI Assistant.');
    this.failed = true;
    this.cdr.markForCheck();
  };

  private refreshProps(): void {
    if (!this.enabled || !this.noteId || this.destroyed || this.loggingOut || this.retired) return;
    if (this.awaitingLogin) return;
    this.connection ??= this.createConnection();
    this.assistantProps = {
      noteId: this.noteId,
      connection: this.connection,
      paragraphs: this.paragraphs,
      onOpenParagraph: this.onOpenParagraph,
      slots: this.slots.slots.value,
      panelWidth: this.panelWidth,
      onPanelWidthChange: this.onPanelWidthChange,
      onPanelVisibilityChange: this.onPanelVisibilityChange,
      subscribePanelClose: this.subscribePanelClose,
      onError: this.onError
    };
    this.cdr.markForCheck();
  }
}
