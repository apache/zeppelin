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
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges
} from '@angular/core';
import { Subject } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';

import { NzCheckboxOption } from 'ng-zorro-antd/checkbox';

import { DynamicForms, DynamicFormsItem, DynamicFormsType, DynamicFormParams } from '@zeppelin/sdk';

@Component({
  selector: 'zeppelin-notebook-paragraph-dynamic-forms',
  templateUrl: './dynamic-forms.component.html',
  styleUrls: ['./dynamic-forms.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false
})
export class NotebookParagraphDynamicFormsComponent implements OnInit, OnChanges, OnDestroy {
  private destroy$ = new Subject<void>();

  @Input() formDefs!: DynamicForms;
  @Input() paramDefs!: DynamicFormParams;
  @Input() runOnChange?: boolean = false;
  @Input() disable = false;
  @Input() removable = false;
  @Output() readonly formChange = new EventEmitter<void>();
  @Output() readonly formRemove = new EventEmitter<DynamicFormsItem>();

  formChange$ = new Subject<void>();
  forms: DynamicFormsItem[] = [];
  formType = DynamicFormsType;
  checkboxGroups: {
    [key: string]: NzCheckboxOption[];
  } = {};
  checkboxValues: {
    [key: string]: Array<string | number>;
  } = {};

  @HostListener('keydown.enter')
  onEnter() {
    if (!this.runOnChange) {
      this.formChange.emit();
    }
  }

  trackByNameFn(_index: number, form: DynamicFormsItem) {
    return form.name;
  }

  setForms() {
    this.forms = Object.values(this.formDefs);
    this.checkboxGroups = {};
    this.forms.forEach(e => {
      if (!this.paramDefs[e.name]) {
        this.paramDefs[e.name] = e.defaultValue;
      }
      if (e.type === DynamicFormsType.CheckBox) {
        // CheckBox type should have defined 'options'.
        // ng-zorro v19 split nz-checkbox-group into `nzOptions` (the {label, value}
        // choices) and an ngModel that holds the selected values directly, instead
        // of a single array of {label, value, checked} objects.
        this.checkboxGroups[e.name] = e.options!.map(opt => ({
          label: opt.displayName || opt.value,
          value: opt.value
        }));
        const param = this.paramDefs[e.name];
        this.checkboxValues[e.name] = Array.isArray(param) ? [...param] : [];
      }
    });
  }

  checkboxChange(value: Array<string | number>, name: string) {
    this.paramDefs[name] = value as string[];
    this.onFormChange();
  }

  onFormChange() {
    if (this.runOnChange) {
      this.formChange$.next();
    }
  }

  remove(item: DynamicFormsItem) {
    this.formRemove.emit(item);
  }

  constructor() {}

  ngOnInit() {
    this.setForms();
    this.formChange$.pipe(debounceTime(800), takeUntil(this.destroy$)).subscribe(() => this.formChange.emit());
  }

  ngOnChanges(_changes: SimpleChanges): void {
    this.setForms();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
