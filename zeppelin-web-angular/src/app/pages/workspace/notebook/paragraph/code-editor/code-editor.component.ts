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
import { editor as MonacoEditor, IDisposable, IPosition, KeyCode, Position } from 'monaco-editor';

import { InterpreterBindingItem } from '@zeppelin/sdk';
import { CompletionService, MessageService } from '@zeppelin/services';

import { pt2px } from '@zeppelin/utility';
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
  @Input() position: IPosition | null = null;
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
  @Output() readonly toggleEditorShow = new EventEmitter<void>();
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
      editor.onDidChangeCursorPosition(e => {
        this.ngZone.run(() => {
          this.position = e.position;
        });
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

  // Handle Ctrl+Alt+E: Toggle editor show/hide
  handleToggleEditorShow() {
    this.toggleEditorShow.emit();
  }

  // Handle Ctrl+K: Cut current line to clipboard
  async handleCutLine() {
    if (!this.editor) {
      return;
    }

    const position = this.editor.getPosition();
    const model = this.editor.getModel();
    if (!position || !model) {
      return;
    }

    const lineNumber = position.lineNumber;
    const lineContent = model.getLineContent(lineNumber);

    if (!lineContent) {
      return;
    }

    await navigator.clipboard.writeText(lineContent);

    this.editor.executeEdits('cut-line', [
      {
        range: new monaco.Range(lineNumber, 1, lineNumber, lineContent.length + 1),
        text: '',
        forceMoveMarkers: true
      }
    ]);
  }

  // Handle Ctrl+Y: Paste from clipboard at current position
  async handlePasteFromClipboard() {
    if (!this.editor) {
      return;
    }

    const text = await navigator.clipboard.readText();
    const position = this.editor.getPosition();
    if (position) {
      this.editor.executeEdits('my-source', [
        {
          range: new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column),
          text: text,
          forceMoveMarkers: true
        }
      ]);
    }
  }

  // Handle Ctrl+S: Show find widget
  handleShowFind() {
    if (this.editor) {
      this.editor.getAction('actions.find').run();

      // Focus on the find widget input field
      const findInput = document.querySelector('.find-widget .input') as HTMLInputElement;
      findInput.focus();
      findInput.select();
    }
  }

  setCursorPosition({ lineNumber, column }: IPosition) {
    if (this.editor) {
      this.editor.setPosition({ lineNumber, column });
    }
  }

  setRestorePosition() {
    if (this.editor) {
      const previousPosition = this.position ?? { lineNumber: 0, column: 0 };
      this.setCursorPosition(previousPosition);
      this.editor.focus();
    }
  }

  setCursorPositionToBeginning() {
    if (this.editor) {
      this.setCursorPosition({ lineNumber: 0, column: 0 });
      this.editor.focus();
    }
  }

  setCursorPositionToEnd() {
    if (this.editor) {
      const lineNumber = this.editor.getModel()?.getLineCount() ?? 0;
      const column = this.editor.getModel()?.getLineMaxColumn(lineNumber) ?? 0;
      this.setCursorPosition({ lineNumber, column });
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
    this.editor.addCommand(monaco.KeyMod.WinCtrl | monaco.KeyMod.Alt | monaco.KeyCode.KeyE, () => {
      this.handleToggleEditorShow();
    });
    this.editor.addCommand(monaco.KeyMod.WinCtrl | monaco.KeyCode.KeyK, () => {
      this.handleCutLine();
    });
    this.editor.addCommand(monaco.KeyMod.WinCtrl | monaco.KeyCode.KeyY, () => {
      this.handlePasteFromClipboard();
    });
    this.editor.addCommand(monaco.KeyMod.WinCtrl | monaco.KeyCode.KeyS, () => {
      this.handleShowFind();
    });

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
      },
      find: {
        addExtraSpaceOnTop: false
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
