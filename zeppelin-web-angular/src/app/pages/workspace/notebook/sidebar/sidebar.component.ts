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

import { ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';

import { Note } from '@zeppelin/sdk';
import { AssistantSlots } from '../assistant/assistant-slots';

enum SidebarState {
  CLOSED = 'CLOSED',
  FILE_TREE = 'FILE_TREE',
  TOC = 'TOC'
}

@Component({
  selector: 'zeppelin-notebook-sidebar',
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.less'],
  standalone: false
})
export class NotebookSidebarComponent implements OnInit, OnDestroy {
  @Input() note!: Exclude<Note['note'], undefined>;
  @Output() readonly isSidebarOpenChange = new EventEmitter<boolean>();
  @Output() readonly scrollToParagraph = new EventEmitter<string>();
  sidebarState = SidebarState.CLOSED;
  SidebarState = SidebarState;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly cdr: ChangeDetectorRef,
    private readonly assistantSlots: AssistantSlots
  ) {}

  ngOnInit(): void {
    this.assistantSlots.panelOpen.pipe(filter(Boolean), takeUntil(this.destroy$)).subscribe(() => {
      if (this.sidebarState !== SidebarState.CLOSED) {
        this.sidebarState = SidebarState.CLOSED;
        this.isSidebarOpenChange.emit(false);
        this.cdr.markForCheck();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  setOrToggleSidebarState(sidebarState: SidebarState) {
    if (this.sidebarState === sidebarState) {
      this.sidebarState = SidebarState.CLOSED;
    } else {
      this.sidebarState = sidebarState;
    }
    if (this.sidebarState === SidebarState.CLOSED) {
      this.isSidebarOpenChange.emit(false);
    } else {
      this.assistantSlots.requestPanelClose();
      this.isSidebarOpenChange.emit(true);
    }
  }

  onScrollToParagraph(event: string) {
    this.scrollToParagraph.emit(event);
  }
}
