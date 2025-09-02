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
import { editor as MonacoEditor, IDisposable, KeyCode } from 'monaco-editor';

import { InterpreterBindingItem } from '@zeppelin/sdk';
import { CompletionService, MessageService } from '@zeppelin/services';

import { pt2px } from '@zeppelin/utility/css-unit-conversion';
import { NotebookParagraphControlComponent } from '../control/control.component';

type IStandaloneCodeEditor = MonacoEditor.IStandaloneCodeEditor;
type IEditor = MonacoEditor.IEditor;

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
  @Input() paragraphControl!: NotebookParagraphControlComponent;
  @Input() lineNumbers = false;
  @Input() focus = false;
  @Input() collaborativeMode = false;
  @Input() text!: string;
  @Input() fontSize: number | undefined;
  @Input() dirty = false;
  @Input() interpreterBindings: InterpreterBindingItem[] = [];
  @Input() pid!: string;
  @Output() readonly textChanged = new EventEmitter<string>();
  @Output() readonly editorBlur = new EventEmitter<void>();
  @Output() readonly editorFocus = new EventEmitter<void>();
  private editor?: IStandaloneCodeEditor;
  private monacoDisposables: IDisposable[] = [];
  height = 18;
  interpreterName?: string;

  autoAdjustEditorHeight() {
    const editor = this.editor;
    const model = editor?.getModel();
    if (editor && model) {
      this.ngZone.run(() => {
        this.height = editor.getOption(monaco.editor.EditorOption.lineHeight) * (model.getLineCount() + 2);
        editor.layout();
        this.cdr.markForCheck();
      });
    }
  }

  initEditorListener(editor: IStandaloneCodeEditor) {
    this.monacoDisposables.push(
      editor.onDidFocusEditorText(() => {
        this.editorFocus.emit();
      }),
      editor.onDidBlurEditorText(() => {
        this.editorBlur.emit();
      }),

      editor.onDidChangeModelContent(() => {
        this.ngZone.run(() => {
          const model = editor.getModel();
          if (!model) {
            throw new Error('Model content changed but model not found.');
          }
          this.text = model.getValue();
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

  setEditorValue(editor: IStandaloneCodeEditor) {
    const model = editor.getModel();
    if (model && model.getValue() !== this.text) {
      model.setValue(this.text || '');
    }
  }

  initializedEditor(editor: IEditor) {
    this.editor = editor as IStandaloneCodeEditor;
    this.editor.addCommand(
      KeyCode.Escape,
      () => {
        if (document.activeElement instanceof HTMLElement) {
          document.activeElement.blur();
        }
      },
      '!suggestWidgetVisible'
    );

    this.updateEditorOptions(this.editor);
    this.setParagraphMode();
    this.initEditorListener(this.editor);
    this.initEditorFocus();
    this.initCompletionService(this.editor);
    this.setEditorValue(this.editor);
    setTimeout(() => {
      this.autoAdjustEditorHeight();
    });
  }

  initCompletionService(editor: IStandaloneCodeEditor): void {
    const model = editor.getModel();
    if (!model) {
      return;
    }
    this.completionService.registerAsCompletionReceiver(model, this.paragraphControl.pid);
  }

  initEditorFocus() {
    if (this.focus && this.editor) {
      this.editor.focus();
    }
  }

  updateEditorOptions(editor: IStandaloneCodeEditor) {
    editor.updateOptions({
      readOnly: this.readOnly,
      fontSize: this.fontSize && pt2px(this.fontSize),
      renderLineHighlight: this.focus ? 'all' : 'none',
      minimap: { enabled: false },
      lineNumbers: this.lineNumbers ? 'on' : 'off',
      glyphMargin: false,
      folding: false,
      scrollBeyondLastLine: false,
      contextmenu: false,
      matchBrackets: 'always',
      wordWrap: 'on',
      scrollbar: {
        handleMouseWheel: false,
        alwaysConsumeMouseWheel: false
      }
    });
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
      if (!model) {
        return;
      }
      if (this.language) {
        // TODO(hsuanxyz): config convertMap
        const convertMap: Record<string, string> = {
          sh: 'shell'
        };
        MonacoEditor.setModelLanguage(model, convertMap[this.language] || this.language);
      }
    } else {
      const interpreterName = this.getInterpreterName(this.text);
      if (this.interpreterName !== interpreterName) {
        this.interpreterName = interpreterName;
        this.getEditorSetting();
      }
    }
  }

  getEditorSetting() {
    this.messageService.editorSetting(this.pid, this.text);
  }

  layout() {
    if (this.editor) {
      setTimeout(() => {
        this.editor!.layout();
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
      if (this.editor) {
        this.updateEditorOptions(this.editor);
      }
    }
    if (focus) {
      this.initEditorFocus();
    }
    if (text) {
      if (this.editor) {
        this.setEditorValue(this.editor);
      }
    }

    if (interpreterBindings || language) {
      this.setParagraphMode();
    }
    if (text || fontSize) {
      this.autoAdjustEditorHeight();
    }
  }

  ngOnDestroy(): void {
    const model = this.editor?.getModel();
    if (model) {
      this.completionService.unregister(model);
    }
    this.monacoDisposables.forEach(d => d.dispose());
  }

  ngAfterViewInit(): void {}
}
