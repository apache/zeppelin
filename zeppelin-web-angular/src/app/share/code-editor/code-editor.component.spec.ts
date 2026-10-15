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

import { ElementRef, NgZone, SimpleChange } from '@angular/core';
import { editor } from 'monaco-editor';
import { BehaviorSubject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CodeEditorComponent } from './code-editor.component';
import { CodeEditorService } from './code-editor.service';
import { JoinedEditorOptions, NzEditorMode } from './nz-code-editor.definitions';

describe('CodeEditorComponent models', () => {
  const components: CodeEditorComponent[] = [];
  let initialModels: Set<editor.ITextModel>;

  beforeEach(() => {
    initialModels = new Set(editor.getModels());
    // Replace only the DOM views; model contents, events and disposal use Monaco.
    vi.spyOn(editor, 'create').mockImplementation(() => {
      let model: editor.ITextModel | null = null;
      return {
        getModel: () => model,
        setModel: (next: editor.ITextModel | null) => {
          model = next;
        },
        dispose: () => {},
        updateOptions: () => {}
      } as unknown as editor.IStandaloneCodeEditor;
    });
    vi.spyOn(editor, 'createDiffEditor').mockImplementation(() => {
      let model: editor.IDiffEditorModel | null = null;
      return {
        getModel: () => model,
        setModel: (next: editor.IDiffEditorModel | null) => {
          model = next;
        },
        dispose: () => {},
        updateOptions: () => {}
      } as unknown as editor.IStandaloneDiffEditor;
    });
  });

  afterEach(() => {
    for (const component of components.splice(0)) {
      component.ngOnDestroy();
    }
    // Clean up external models and any leaks left by a failing regression.
    for (const model of editor.getModels()) {
      if (!initialModels.has(model)) {
        model.dispose();
      }
    }
    vi.restoreAllMocks();
  });

  const createComponent = (mode: NzEditorMode = 'normal') => {
    const service = {
      requestToInit: () => of({}),
      option$: new BehaviorSubject<JoinedEditorOptions>({})
    } as Pick<CodeEditorService, 'requestToInit' | 'option$'>;
    const component = new CodeEditorComponent(
      service as CodeEditorService,
      new NgZone({}),
      new ElementRef(document.createElement('div'))
    );
    component.nzEditorMode = mode;
    components.push(component);
    return component;
  };

  const initialize = (component: CodeEditorComponent) => {
    let instance: editor.IEditor | editor.IDiffEditor | undefined;
    component.nzEditorInitialized.subscribe(value => {
      instance = value;
    });
    component.ngAfterViewInit();
    return instance!;
  };

  const destroy = (component: CodeEditorComponent) => {
    components.splice(components.indexOf(component), 1);
    component.ngOnDestroy();
  };

  it('initializes the original on the left and the bound value on the right', () => {
    const component = createComponent('diff');
    component.nzOriginalText = 'before';
    component.writeValue('after');
    const instance = initialize(component) as editor.IDiffEditor;

    expect(instance.getModel()!.original.getValue()).toBe('before');
    expect(instance.getModel()!.modified.getValue()).toBe('after');
  });

  it('reuses both diff models across bound updates and continues reporting modified edits', () => {
    const component = createComponent('diff');
    const onChange = vi.fn();
    component.registerOnChange(onChange);
    const instance = initialize(component) as editor.IDiffEditor;
    const { original, modified } = instance.getModel()!;
    component.writeValue('second');
    component.writeValue('third');

    expect(instance.getModel()!.original).toBe(original);
    expect(instance.getModel()!.modified).toBe(modified);
    expect(modified.getValue()).toBe('third');
    modified.applyEdits([{ range: modified.getFullModelRange(), text: 'user edit' }]);
    expect(onChange).toHaveBeenLastCalledWith('user edit');
  });

  it('updates the original input without changing the modified text or model identities', () => {
    const component = createComponent('diff');
    component.nzOriginalText = 'before';
    component.writeValue('after');
    const instance = initialize(component) as editor.IDiffEditor;
    const { original, modified } = instance.getModel()!;
    modified.applyEdits([{ range: modified.getFullModelRange(), text: 'user edit' }]);
    const onChange = vi.fn();
    component.registerOnChange(onChange);

    component.nzOriginalText = 'new baseline';
    component.ngOnChanges({ nzOriginalText: new SimpleChange('before', 'new baseline', false) });

    expect(original.getValue()).toBe('new baseline');
    expect(modified.getValue()).toBe('user edit');
    expect(instance.getModel()!.original).toBe(original);
    expect(instance.getModel()!.modified).toBe(modified);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('clears either diff input without replacing its models', () => {
    const component = createComponent('diff');
    component.nzOriginalText = 'before';
    component.writeValue('after');
    const instance = initialize(component) as editor.IDiffEditor;
    const { original, modified } = instance.getModel()!;
    component.writeValue('');
    expect(modified.getValue()).toBe('');
    expect(original.getValue()).toBe('before');

    component.nzOriginalText = '';
    component.ngOnChanges({ nzOriginalText: new SimpleChange('before', '', false) });
    expect(original.getValue()).toBe('');
    expect(instance.getModel()!.original).toBe(original);
    expect(instance.getModel()!.modified).toBe(modified);
  });

  it('preserves normal model reuse on bound updates', () => {
    const component = createComponent();
    component.writeValue('before');
    const instance = initialize(component) as editor.IEditor;
    const model = instance.getModel() as editor.ITextModel;
    component.writeValue('after');

    expect(instance.getModel()).toBe(model);
    expect(model.getValue()).toBe('after');
  });

  it.each(['normal', 'diff'] as const)('leaves no owned models after repeated %s lifecycles', mode => {
    const baseline = editor.getModels().map(model => model.uri.toString());
    for (let iteration = 0; iteration < 3; iteration++) {
      const component = createComponent(mode);
      initialize(component);
      component.writeValue('updated');
      destroy(component);
    }

    expect(editor.getModels().map(model => model.uri.toString())).toEqual(baseline);
  });

  it('updates a normal replacement model while disposing only its detached owned model', () => {
    const component = createComponent();
    const instance = initialize(component) as editor.IEditor;
    const owned = instance.getModel() as editor.ITextModel;
    const external = editor.createModel('caller owned');
    instance.setModel(external);
    component.writeValue('bound update');

    expect((instance.getModel() as editor.ITextModel).getValue()).toBe('bound update');
    expect(owned.getValue()).toBe('');
    destroy(component);
    expect(owned.isDisposed()).toBe(true);
    expect(external.isDisposed()).toBe(false);
  });

  it('disposes both detached diff models without disposing a caller-owned replacement pair', () => {
    const component = createComponent('diff');
    const instance = initialize(component) as editor.IDiffEditor;
    const owned = instance.getModel()!;
    const external = {
      original: editor.createModel('external original'),
      modified: editor.createModel('external modified')
    };
    instance.setModel(external);
    destroy(component);

    expect(owned.original.isDisposed()).toBe(true);
    expect(owned.modified.isDisposed()).toBe(true);
    expect(external.original.isDisposed()).toBe(false);
    expect(external.modified.isDisposed()).toBe(false);
  });
});
