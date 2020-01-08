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
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges
} from '@angular/core';

import { editor as MonacoEditor, IDisposable } from 'monaco-editor';
import IStandaloneCodeEditor = MonacoEditor.IStandaloneCodeEditor;
import IEditor = monaco.editor.IEditor;

import { InterpreterBindingItem } from '@zeppelin/sdk';
import { CompletionService, MessageService } from '@zeppelin/services';

import { NotebookParagraphControlComponent } from '../control/control.component';

@Component({
  selector: 'zeppelin-notebook-paragraph-code-editor',
  templateUrl: './code-editor.component.html',
  styleUrls: ['./code-editor.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookParagraphCodeEditorComponent implements OnChanges, OnDestroy, AfterViewInit {
  // TODO(hsuanxyz):
  //  1. cursor position
  @Input() readOnly = false;
  @Input() language = 'text';
  @Input() paragraphControl: NotebookParagraphControlComponent;
  @Input() lineNumbers = false;
  @Input() focus = false;
  @Input() collaborativeMode = false;
  @Input() text: string;
  @Input() fontSize: number;
  @Input() dirty = false;
  @Input() interpreterBindings: InterpreterBindingItem[] = [];
  @Input() pid: string;
  @Output() readonly textChanged = new EventEmitter<string>();
  @Output() readonly editorBlur = new EventEmitter<void>();
  @Output() readonly editorFocus = new EventEmitter<void>();
  private editor: IStandaloneCodeEditor;
  private monacoDisposables: IDisposable[] = [];
  height = 0;
  interpreterName: string;

  autoAdjustEditorHeight() {
    if (this.editor) {
      this.ngZone.run(() => {
        this.height =
          this.editor.getTopForLineNumber(Number.MAX_SAFE_INTEGER) + this.editor.getConfiguration().lineHeight * 2;
        this.editor.layout();
        this.cdr.markForCheck();
      });
    }
  }

  initEditorListener() {
    const editor = this.editor;
    this.monacoDisposables.push(
      editor.onDidFocusEditorText(() => {
        this.editorFocus.emit();
      }),
      editor.onDidBlurEditorText(() => {
        this.editorBlur.emit();
      }),

      editor.onDidChangeModelContent(() => {
        this.ngZone.run(() => {
          this.text = editor.getModel().getValue();
          this.textChanged.emit(this.text);
          this.setParagraphMode(true);
          this.autoAdjustEditorHeight();
          setTimeout(() => {
            this.autoAdjustEditorHeight();
          });
        });
      })
    );
  }

  setEditorValue() {
    if (this.editor && this.editor.getModel() && this.editor.getModel().getValue() !== this.text) {
      this.editor.getModel().setValue(this.text || '');
    }
  }

  initializedEditor(editor: IEditor) {
    this.editor = editor as IStandaloneCodeEditor;
    this.editor.addCommand(
      monaco.KeyCode.Escape,
      () => {
        if (document.activeElement instanceof HTMLElement) {
          document.activeElement.blur();
        }
      },
      '!suggestWidgetVisible'
    );

    this.updateEditorOptions();
    this.setParagraphMode();
    this.initEditorListener();
    this.initEditorFocus();
    this.initCompletionService();
    this.setEditorValue();
    setTimeout(() => {
      this.autoAdjustEditorHeight();
    });
  }

  initCompletionService(): void {
    this.completionService.registerAsCompletionReceiver(this.editor.getModel(), this.paragraphControl.pid);
  }

  initEditorFocus() {
    if (this.focus && this.editor) {
      this.editor.focus();
    }
  }

  updateEditorOptions() {
    if (this.editor) {
      this.editor.updateOptions({
        readOnly: this.readOnly,
        fontSize: this.fontSize,
        renderLineHighlight: this.focus ? 'all' : 'none',
        minimap: { enabled: false },
        lineNumbers: this.lineNumbers ? 'on' : 'off',
        glyphMargin: false,
        folding: false,
        scrollBeyondLastLine: false,
        contextmenu: false,
        matchBrackets: false
      });
    }
  }

  getInterpreterName(paragraphText: string) {
    const match = /^\s*%(.+?)(\s|\()/g.exec(paragraphText);
    if (match) {
      return match[1].trim();
      // get default interpreter name if paragraph text doesn't start with '%'
      // TODO(hsuanxyz): dig into the cause what makes interpreterBindings to have no element
    } else if (this.interpreterBindings && this.interpreterBindings.length !== 0) {
      return this.interpreterBindings[0].name;
    }
    return '';
  }

  setParagraphMode(changed = false) {
    if (this.editor && !changed) {
      const model = this.editor.getModel();
      if (this.language) {
        // TODO(hsuanxyz): config convertMap
        const convertMap = {
          sh: 'shell'
        };
        monaco.editor.setModelLanguage(model, convertMap[this.language] || this.language);
      }
    } else {
      const interpreterName = this.getInterpreterName(this.text);
      if (this.interpreterName !== interpreterName) {
        this.interpreterName = interpreterName;
        this.getEditorSetting(interpreterName);
      }
    }
  }

  getEditorSetting(interpreterName: string) {
    this.messageService.editorSetting(this.pid, interpreterName);
  }

  layout() {
    if (this.editor) {
      setTimeout(() => {
        this.editor.layout();
      });
    }
  }

  constructor(
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone,
    private messageService: MessageService,
    private completionService: CompletionService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    const { text, interpreterBindings, language, readOnly, focus, lineNumbers, fontSize } = changes;
    if (readOnly || focus || lineNumbers || fontSize) {
      this.updateEditorOptions();
    }
    if (focus) {
      this.initEditorFocus();
    }
    if (text) {
      this.setEditorValue();
    }

    if (interpreterBindings || language) {
      this.setParagraphMode();
    }
    if (text || fontSize) {
      this.autoAdjustEditorHeight();
    }
  }

  ngOnDestroy(): void {
    this.completionService.unregister(this.editor.getModel());
    this.monacoDisposables.forEach(d => d.dispose());
  }

  ngAfterViewInit(): void {}
}
