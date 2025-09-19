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
  @Input() status!: string;
  @Input() progress = 0;
  @Input() revisionView = false;
  @Input() enabled?: boolean = true;
  @Input() pid!: string;
  @Input() tableHide?: boolean = false;
  @Input() editorHide?: boolean = false;
  @Input() colWidth?: number;
  @Input() fontSize?: number;
  @Input() runOnSelectionChange?: boolean = true;
  @Input() isEntireNoteRunning = true;
  @Input() runtimeInfos?: RuntimeInfos;
  @Input() colWidthOption: number[] = [];
  @Input() first = false;
  @Input() last = false;
  @Input() titleShow?: boolean = false;
  @Input() showLineNumbers?: boolean = false;
  @Input() paragraphLength!: number;
  @Output() readonly colWidthChange = new EventEmitter<number>();
  @Output() readonly titleShowChange = new EventEmitter<boolean>();
  @Output() readonly enabledChange = new EventEmitter<boolean>();
  @Output() readonly fontSizeChange = new EventEmitter<number>();
  @Output() readonly tableHideChange = new EventEmitter<boolean>();
  @Output() readonly runParagraph = new EventEmitter();
  @Output() readonly showLineNumbersChange = new EventEmitter<boolean>();
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

  formatShortcut(shortcut: string, isMac: boolean) {
    if (isMac) {
      return shortcut.replace('Alt', 'Option');
    }

    return shortcut;
  }

  updateListOfMenu() {
    this.listOfMenu = [
      {
        label: 'Run',
        show: !this.first,
        disabled: this.isEntireNoteRunning,
        icon: 'play-circle',
        trigger: () => this.trigger(this.runParagraph),
        shortCut: this.formatShortcut('Shift+Enter', this.isMac)
      },
      {
        label: 'Run all above',
        show: !this.first,
        disabled: this.isEntireNoteRunning,
        icon: 'up-square',
        trigger: () => this.trigger(this.runAllAbove),
        shortCut: this.formatShortcut('Shift+Ctrl+Up', this.isMac)
      },
      {
        label: 'Run all below',
        show: !this.last,
        disabled: this.isEntireNoteRunning,
        icon: 'down-square',
        trigger: () => this.trigger(this.runAllBelowAndCurrent),
        shortCut: this.formatShortcut('Shift+Ctrl+Down', this.isMac)
      },
      {
        label: 'Link this paragraph',
        show: true,
        disabled: false,
        icon: 'export',
        trigger: () => {
          this.openSingleParagraph.emit(this.pid);
        },
        shortCut: this.formatShortcut('Ctrl+Alt+W', this.isMac)
      },
      {
        label: 'Clear output',
        show: true,
        disabled: this.isEntireNoteRunning,
        icon: 'fire',
        trigger: () => this.clearParagraphOutput(),
        shortCut: this.formatShortcut('Ctrl+Alt+L', this.isMac)
      },
      {
        label: 'Remove',
        show: this.paragraphLength > 1,
        disabled: this.isEntireNoteRunning,
        icon: 'delete',
        trigger: () => this.onRemoveParagraph(),
        shortCut: this.formatShortcut('Ctrl+Alt+D', this.isMac)
      },
      {
        label: 'Move paragraph up',
        show: !this.first,
        disabled: this.isEntireNoteRunning,
        icon: 'up',
        trigger: () => this.trigger(this.moveUp),
        shortCut: this.formatShortcut('Ctrl+Alt+K', this.isMac)
      },
      {
        label: 'Move paragraph down',
        show: !this.last,
        disabled: this.isEntireNoteRunning,
        icon: 'down',
        trigger: () => this.trigger(this.moveDown),
        shortCut: this.formatShortcut('Ctrl+Alt+J', this.isMac)
      },
      {
        label: 'Insert new',
        show: true,
        disabled: this.isEntireNoteRunning,
        icon: 'plus',
        trigger: () => this.trigger(this.insertNew),
        shortCut: this.formatShortcut('Ctrl+Alt+B', this.isMac)
      },
      {
        label: 'Clone paragraph',
        show: true,
        disabled: this.isEntireNoteRunning,
        icon: 'copy',
        trigger: () => this.trigger(this.cloneParagraph),
        shortCut: this.formatShortcut('Shift+Ctrl+C', this.isMac)
      },
      {
        label: this.titleShow ? 'Hide Title' : 'Show Title',
        show: true,
        disabled: false,
        icon: 'font-colors',
        trigger: () => this.toggleShowTitle(),
        shortCut: this.formatShortcut('Ctrl+Alt+T', this.isMac)
      },
      {
        label: this.showLineNumbers ? 'Hide line numbers' : 'Show line numbers',
        show: true,
        disabled: false,
        icon: 'ordered-list',
        trigger: () => this.toggleLineNumbers(),
        shortCut: this.formatShortcut('Ctrl+Alt+M', this.isMac)
      },
      {
        label: this.enabled ? 'Disable run' : 'Enable run',
        show: true,
        disabled: this.isEntireNoteRunning,
        icon: 'api',
        trigger: () => this.toggleEnabled(),
        shortCut: this.formatShortcut('Ctrl+Alt+R', this.isMac)
      }
    ];
  }

  toggleEditor() {
    this.editorHideChange.emit(!this.editorHide);
  }

  toggleOutput() {
    this.tableHideChange.emit(!this.tableHide);
  }

  toggleRunOnSelectionChange() {
    this.runOnSelectionChangeChange.emit(!this.runOnSelectionChange);
  }

  toggleShowTitle() {
    this.titleShowChange.emit(!this.titleShow);
  }

  toggleLineNumbers() {
    this.showLineNumbersChange.emit(!this.showLineNumbers);
  }

  toggleEnabled() {
    if (!this.isEntireNoteRunning) {
      this.enabledChange.emit(!this.enabled);
    }
  }

  changeColWidth(colWidth: string) {
    this.colWidthChange.emit(+colWidth);
    this.dropdownVisible = false;
  }

  changeFontSize(fontSize: string) {
    this.fontSizeChange.emit(+fontSize);
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
