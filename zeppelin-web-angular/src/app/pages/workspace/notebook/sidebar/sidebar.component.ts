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

import { Component, EventEmitter, Input, Output } from '@angular/core';

import { Note } from '@zeppelin/sdk';

enum SidebarState {
  CLOSED = 'CLOSED',
  FILE_TREE = 'FILE_TREE',
  TOC = 'TOC'
}

@Component({
  selector: 'zeppelin-notebook-sidebar',
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.less']
})
export class NotebookSidebarComponent {
  @Input() note: Note['note'];
  @Output() readonly isSidebarOpenChange = new EventEmitter<boolean>();
  @Output() readonly scrollToParagraph = new EventEmitter<string>();
  sidebarState = SidebarState.CLOSED;
  SidebarState = SidebarState;

  setOrToggleSidebarState(sidebarState: SidebarState) {
    if (this.sidebarState === sidebarState) {
      this.sidebarState = SidebarState.CLOSED;
    } else {
      this.sidebarState = sidebarState;
    }
    if (this.sidebarState === SidebarState.CLOSED) {
      this.isSidebarOpenChange.emit(false);
    } else {
      this.isSidebarOpenChange.emit(true);
    }
  }

  onScrollToParagraph(event: string) {
    this.scrollToParagraph.emit(event);
  }
}
