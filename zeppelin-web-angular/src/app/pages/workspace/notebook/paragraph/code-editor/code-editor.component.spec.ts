/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { ChangeDetectorRef, NgZone } from '@angular/core';
import { editor } from 'monaco-editor';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@zeppelin/services', () => ({
  CompletionService: class {},
  InlineCompletionService: class {},
  MessageService: class {}
}));

import { CompletionService, InlineCompletionService, MessageService } from '@zeppelin/services';
import { NotebookParagraphControlComponent } from '../control/control.component';
import { NotebookParagraphCodeEditorComponent } from './code-editor.component';

describe('Notebook editor without a global Monaco API', () => {
  let component: NotebookParagraphCodeEditorComponent;
  let model: editor.ITextModel;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal('monaco', undefined);
    model = editor.createModel('initial');
    const view = {
      getModel: () => model,
      getOption: () => 20,
      addCommand: () => {},
      updateOptions: () => {},
      layout: () => {},
      onDidFocusEditorText: () => ({ dispose: () => {} }),
      onDidBlurEditorText: () => ({ dispose: () => {} }),
      onDidChangeCursorPosition: () => ({ dispose: () => {} }),
      onDidChangeModelContent: model.onDidChangeContent.bind(model),
      deltaDecorations: () => []
    } as unknown as editor.IStandaloneCodeEditor;
    component = new NotebookParagraphCodeEditorComponent(
      { markForCheck: () => {} } as ChangeDetectorRef,
      new NgZone({}),
      { editorSetting: () => {} } as unknown as MessageService,
      { registerAsCompletionReceiver: () => {}, unregister: () => {} } as unknown as CompletionService,
      { register: () => {}, unregister: () => {} } as unknown as InlineCompletionService
    );
    component.text = 'initial';
    component.paragraphControl = { pid: 'paragraph' } as NotebookParagraphControlComponent;
    component.initializedEditor(view);
  });

  afterEach(() => {
    component.ngOnDestroy();
    model.dispose();
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('emits user edits without a global Monaco API', () => {
    const changed = vi.fn();
    component.textChanged.subscribe(changed);

    model.applyEdits([{ range: model.getFullModelRange(), text: 'edited\nsecond line' }]);

    expect(changed).toHaveBeenCalledExactlyOnceWith('edited\nsecond line');
  });
});
