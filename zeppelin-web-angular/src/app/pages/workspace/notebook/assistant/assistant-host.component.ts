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
import { Subject, takeUntil } from 'rxjs';

import type { AssistantShellProps } from '@zeppelin/sdk';
import { AssistantSlots } from './assistant-slots';

@Component({
  selector: 'zeppelin-assistant-host',
  template: `
    @if (enabled) {
      @if (failed) {
        <p role="alert">Unable to load the assistant.</p>
      } @else if (assistantProps) {
        <div zeppelin-react-mount="./AssistantWorkspace" [reactProps]="assistantProps"></div>
      }
    }
  `,
  standalone: false
})
export class AssistantHostComponent implements OnInit, OnChanges, OnDestroy {
  @Input() enabled = false;
  @Input() noteId!: string;
  @Input() panelWidth!: number;
  @Output() readonly panelWidthChange = new EventEmitter<number>();
  assistantProps: AssistantShellProps | null = null;
  failed = false;

  private readonly destroy$ = new Subject<void>();
  private destroyed = false;

  constructor(
    private readonly slots: AssistantSlots,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.slots.slots.pipe(takeUntil(this.destroy$)).subscribe(() => {
      queueMicrotask(() => {
        if (this.destroyed) return;
        this.refreshProps();
      });
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.noteId && !changes.noteId.firstChange && changes.noteId.previousValue !== this.noteId) {
      this.slots.requestPanelClose();
      this.failed = false;
    }
    if (!this.enabled) {
      this.slots.setPanelOpen(false);
      this.assistantProps = null;
      return;
    }
    if (changes.enabled) this.failed = false;
    this.refreshProps();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.slots.setPanelOpen(false);
    this.destroy$.next();
    this.destroy$.complete();
  }

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

  private readonly onError = (_error: unknown): void => {
    this.failed = true;
    this.cdr.markForCheck();
  };

  private refreshProps(): void {
    if (!this.enabled || !this.noteId) return;
    this.assistantProps = {
      noteId: this.noteId,
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
