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
  forwardRef,
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnDestroy,
  Output,
  TemplateRef,
  ViewEncapsulation
} from '@angular/core';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { combineLatest, fromEvent, BehaviorSubject, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, map, takeUntil } from 'rxjs/operators';

import { warn, InputBoolean } from 'ng-zorro-antd/core';

import { CodeEditorService } from './code-editor.service';
import { DiffEditorOptions, EditorOptions, JoinedEditorOptions, NzEditorMode } from './nz-code-editor.definitions';

// Import types from monaco editor.
import { editor } from 'monaco-editor';
import IEditor = editor.IEditor;
import IDiffEditor = editor.IDiffEditor;
import ITextModel = editor.ITextModel;

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  selector: 'zeppelin-code-editor',
  exportAs: 'CodeEditor',
  templateUrl: './code-editor.component.html',
  host: {
    '[class.ant-code-editor]': 'true'
  },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CodeEditorComponent),
      multi: true
    }
  ]
})
export class CodeEditorComponent implements OnDestroy, AfterViewInit {
  @Input() nzEditorMode: NzEditorMode = 'normal';
  @Input() nzOriginalText = '';
  @Input() @InputBoolean() nzLoading = false;
  @Input() @InputBoolean() nzFullControl = false;
  @Input() nzToolkit: TemplateRef<void>;

  @Input() set nzEditorOption(value: JoinedEditorOptions) {
    this.editorOption$.next(value);
  }

  @Output() readonly nzEditorInitialized = new EventEmitter<IEditor | IDiffEditor>();

  editorOptionCached: JoinedEditorOptions = {};

  private readonly el: HTMLElement;
  private destroy$ = new Subject<void>();
  private resize$ = new Subject<void>();
  private editorOption$ = new BehaviorSubject<JoinedEditorOptions>({});
  private editorInstance: IEditor | IDiffEditor;
  private value = '';
  private modelSet = false;

  constructor(private nzCodeEditorService: CodeEditorService, private ngZone: NgZone, elementRef: ElementRef) {
    this.el = elementRef.nativeElement;
  }

  /**
   * Initialize a monaco editor instance.
   */
  ngAfterViewInit(): void {
    this.nzCodeEditorService.requestToInit().subscribe(option => this.setup(option));
  }

  ngOnDestroy(): void {
    if (this.editorInstance) {
      this.editorInstance.dispose();
    }

    this.destroy$.next();
    this.destroy$.complete();
  }

  writeValue(value: string): void {
    this.value = value;
    this.setValue();
  }

  // tslint:disable-next-line no-any
  registerOnChange(fn: (value: string) => void): any {
    this.onChange = fn;
  }

  // tslint:disable-next-line no-any
  registerOnTouched(fn: any): void {
    this.onTouch = fn;
  }

  onChange(_value: string): void {}

  onTouch(): void {}

  layout(): void {
    this.resize$.next();
  }

  private setup(option: JoinedEditorOptions): void {
    this.editorOptionCached = option;
    this.registerOptionChanges();
    this.initMonacoEditorInstance();
    this.registerResizeChange();
    this.setValue();

    if (!this.nzFullControl) {
      this.setValueEmitter();
    }
    this.nzEditorInitialized.emit(this.editorInstance);
  }

  private registerOptionChanges(): void {
    combineLatest([this.editorOption$, this.nzCodeEditorService.option$])
      .pipe(takeUntil(this.destroy$))
      .subscribe(([selfOpt, defaultOpt]) => {
        this.editorOptionCached = {
          ...this.editorOptionCached,
          ...defaultOpt,
          ...selfOpt
        };
        this.updateOptionToMonaco();
      });
  }

  private initMonacoEditorInstance(): void {
    this.ngZone.runOutsideAngular(() => {
      this.editorInstance =
        this.nzEditorMode === 'normal'
          ? editor.create(this.el, { ...this.editorOptionCached })
          : editor.createDiffEditor(this.el, {
              ...(this.editorOptionCached as DiffEditorOptions)
            });
    });
  }

  private registerResizeChange(): void {
    this.ngZone.runOutsideAngular(() => {
      fromEvent(window, 'resize')
        .pipe(
          debounceTime(300),
          takeUntil(this.destroy$)
        )
        .subscribe(() => {
          this.layout();
        });

      this.resize$
        .pipe(
          takeUntil(this.destroy$),
          filter(() => !!this.editorInstance),
          map(() => ({
            width: this.el.clientWidth,
            height: this.el.clientHeight
          })),
          distinctUntilChanged((a, b) => a.width === b.width && a.height === b.height),
          debounceTime(50)
        )
        .subscribe(() => {
          this.editorInstance.layout();
        });
    });
  }

  private setValue(): void {
    if (!this.editorInstance) {
      return;
    }

    if (this.nzFullControl && this.value) {
      warn(`should not set value when you are using full control mode! It would result in ambiguous data flow!`);
      return;
    }

    if (this.nzEditorMode === 'normal') {
      if (this.modelSet) {
        (this.editorInstance.getModel() as ITextModel).setValue(this.value);
      } else {
        (this.editorInstance as IEditor).setModel(
          editor.createModel(this.value, (this.editorOptionCached as EditorOptions).language)
        );
        this.modelSet = true;
      }
    } else {
      if (this.modelSet) {
        const model = (this.editorInstance as IDiffEditor).getModel()!;
        model.modified.setValue(this.value);
        model.original.setValue(this.nzOriginalText);
      } else {
        const language = (this.editorOptionCached as EditorOptions).language;
        (this.editorInstance as IDiffEditor).setModel({
          original: editor.createModel(this.value, language),
          modified: editor.createModel(this.nzOriginalText, language)
        });
      }
    }
  }

  private setValueEmitter(): void {
    const model = (this.nzEditorMode === 'normal'
      ? (this.editorInstance as IEditor).getModel()
      : (this.editorInstance as IDiffEditor).getModel()!.modified) as ITextModel;

    model.onDidChangeContent(() => {
      this.emitValue(model.getValue());
    });
  }

  private emitValue(value: string): void {
    this.value = value;
    this.onChange(value);
  }

  private updateOptionToMonaco(): void {
    if (this.editorInstance) {
      this.editorInstance.updateOptions({ ...this.editorOptionCached });
    }
  }
}
