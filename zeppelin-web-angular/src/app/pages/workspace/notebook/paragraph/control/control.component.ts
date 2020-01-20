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

/// <reference path="../../../../../../../node_modules/monaco-editor/monaco.d.ts" />
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output
} from '@angular/core';
import { copyTextToClipboard } from '@zeppelin/core';

import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalService } from 'ng-zorro-antd/modal';

import { ActivatedRoute } from '@angular/router';
import { RuntimeInfos } from '@zeppelin/sdk';
import { MessageService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-notebook-paragraph-control',
  exportAs: 'paragraphControl',
  templateUrl: './control.component.html',
  styleUrls: ['./control.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookParagraphControlComponent implements OnInit, OnChanges {
  @Input() status: string;
  @Input() progress = 0;
  @Input() revisionView = false;
  @Input() enabled = true;
  @Input() pid: string;
  @Input() tableHide = false;
  @Input() editorHide = false;
  @Input() colWidth: number;
  @Input() fontSize: number;
  @Input() runOnSelectionChange = true;
  @Input() isEntireNoteRunning = true;
  @Input() runtimeInfos: RuntimeInfos;
  @Input() colWidthOption = [];
  @Input() first = false;
  @Input() last = false;
  @Input() title = false;
  @Input() lineNumbers = false;
  @Input() paragraphLength: number;
  @Output() readonly colWidthChange = new EventEmitter<number>();
  @Output() readonly titleChange = new EventEmitter<boolean>();
  @Output() readonly enabledChange = new EventEmitter<boolean>();
  @Output() readonly fontSizeChange = new EventEmitter<number>();
  @Output() readonly tableHideChange = new EventEmitter<boolean>();
  @Output() readonly runParagraph = new EventEmitter();
  @Output() readonly lineNumbersChange = new EventEmitter<boolean>();
  @Output() readonly cancelParagraph = new EventEmitter();
  @Output() readonly editorHideChange = new EventEmitter<boolean>();
  @Output() readonly runOnSelectionChangeChange = new EventEmitter<boolean>();
  @Output() readonly moveUp = new EventEmitter<void>();
  @Output() readonly moveDown = new EventEmitter<void>();
  @Output() readonly insertNew = new EventEmitter<void>();
  @Output() readonly runAllAbove = new EventEmitter<void>();
  @Output() readonly runAllBelowAndCurrent = new EventEmitter<void>();
  @Output() readonly cloneParagraph = new EventEmitter<void>();
  @Output() readonly removeParagraph = new EventEmitter<void>();
  @Output() readonly openSingleParagraph = new EventEmitter<string>();
  fontSizeOption = [9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20];
  dropdownVisible = false;
  isMac = navigator.appVersion.indexOf('Mac') !== -1;
  listOfMenu: Array<{
    label: string;
    show: boolean;
    disabled: boolean;
    icon: string;
    shortCut: string;
    trigger(): void;
  }> = [];

  updateListOfMenu() {
    this.listOfMenu = [
      {
        label: 'Run',
        show: !this.first,
        disabled: this.isEntireNoteRunning,
        icon: 'play-circle',
        trigger: () => this.trigger(this.runParagraph),
        shortCut: this.isMac ? '⇧+⌘+Enter' : 'Shift+Ctrl+Enter'
      },
      {
        label: 'Run all above',
        show: !this.first,
        disabled: this.isEntireNoteRunning,
        icon: 'up-square',
        trigger: () => this.trigger(this.runAllAbove),
        shortCut: this.isMac ? '⇧+⌘+Enter' : 'Shift+Ctrl+Enter'
      },
      {
        label: 'Run all below',
        show: !this.last,
        disabled: this.isEntireNoteRunning,
        icon: 'down-square',
        trigger: () => this.trigger(this.runAllBelowAndCurrent),
        shortCut: this.isMac ? '⇧+⌘+Enter' : 'Shift+Ctrl+Enter'
      },
      {
        label: 'Link this paragraph',
        show: true,
        disabled: false,
        icon: 'export',
        trigger: () => {
          this.openSingleParagraph.emit(this.pid);
        },
        shortCut: this.isMac ? '⌥+⌘+T' : 'Alt+Ctrl+T'
      },
      {
        label: 'Clear output',
        show: true,
        disabled: this.isEntireNoteRunning,
        icon: 'fire',
        trigger: () => this.clearParagraphOutput(),
        shortCut: this.isMac ? '⌥+⌘+L' : 'Alt+Ctrl+L'
      },
      {
        label: 'Remove',
        show: this.paragraphLength > 1,
        disabled: this.isEntireNoteRunning,
        icon: 'delete',
        trigger: () => this.onRemoveParagraph(),
        shortCut: this.isMac ? '⇧+Del (Command)' : 'Shift+Del (Command)'
      },
      {
        label: 'Move up',
        show: !this.first,
        disabled: this.isEntireNoteRunning,
        icon: 'up',
        trigger: () => this.trigger(this.moveUp),
        shortCut: `${this.isMac ? '⌘' : 'Ctrl'}+K (Command)`
      },
      {
        label: 'Move down',
        show: !this.last,
        disabled: this.isEntireNoteRunning,
        icon: 'down',
        trigger: () => this.trigger(this.moveDown),
        shortCut: `${this.isMac ? '⌘' : 'Ctrl'}+J (Command)`
      },
      {
        label: 'Insert new',
        show: true,
        disabled: this.isEntireNoteRunning,
        icon: 'plus',
        trigger: () => this.trigger(this.insertNew),
        shortCut: `B (Command)`
      },
      {
        label: 'Clone paragraph',
        show: true,
        disabled: this.isEntireNoteRunning,
        icon: 'copy',
        trigger: () => this.trigger(this.cloneParagraph),
        shortCut: `C (Command)`
      },
      {
        label: this.title ? 'Hide Title' : 'Show Title',
        show: true,
        disabled: false,
        icon: 'font-colors',
        trigger: () => this.toggleTitle(),
        shortCut: `T (Command)`
      },
      {
        label: this.lineNumbers ? 'Hide line numbers' : 'Show line numbers',
        show: true,
        disabled: false,
        icon: 'ordered-list',
        trigger: () => this.toggleLineNumbers(),
        shortCut: `L (Command)`
      },
      {
        label: this.enabled ? 'Disable run' : 'Enable run',
        show: true,
        disabled: this.isEntireNoteRunning,
        icon: 'api',
        trigger: () => this.toggleEnabled(),
        shortCut: `R (Command)`
      }
    ];
  }

  toggleEditor() {
    this.editorHide = !this.editorHide;
    this.editorHideChange.emit(this.editorHide);
  }

  toggleOutput() {
    this.tableHide = !this.tableHide;
    this.tableHideChange.emit(this.tableHide);
  }

  toggleRunOnSelectionChange() {
    this.runOnSelectionChange = !this.runOnSelectionChange;
    this.runOnSelectionChangeChange.emit(this.runOnSelectionChange);
  }

  toggleTitle() {
    this.title = !this.title;
    this.titleChange.emit(this.title);
  }

  toggleLineNumbers() {
    this.lineNumbers = !this.lineNumbers;
    this.lineNumbersChange.emit(this.lineNumbers);
  }

  toggleEnabled() {
    if (!this.isEntireNoteRunning) {
      this.enabled = !this.enabled;
      this.enabledChange.emit(this.enabled);
    }
  }

  changeColWidth(colWidth: number) {
    this.colWidth = +colWidth;
    this.colWidthChange.emit(this.colWidth);
    this.dropdownVisible = false;
  }

  changeFontSize(fontSize: number) {
    this.fontSize = +fontSize;
    this.fontSizeChange.emit(this.fontSize);
  }

  copyClipboard(id: string) {
    copyTextToClipboard(id);
    this.nzMessageService.info('Paragraph id copied');
  }

  clearParagraphOutput() {
    if (!this.isEntireNoteRunning) {
      this.messageService.paragraphClearOutput(this.pid);
    }
  }

  onRemoveParagraph() {
    this.removeParagraph.emit();
  }

  trigger(event: EventEmitter<void>) {
    if (!this.isEntireNoteRunning) {
      this.dropdownVisible = false;
      event.emit();
    }
  }

  constructor(
    private cdr: ChangeDetectorRef,
    private nzMessageService: NzMessageService,
    private activatedRoute: ActivatedRoute,
    private messageService: MessageService
  ) {}

  ngOnInit() {
    this.updateListOfMenu();
  }

  ngOnChanges(): void {
    this.updateListOfMenu();
  }
}
