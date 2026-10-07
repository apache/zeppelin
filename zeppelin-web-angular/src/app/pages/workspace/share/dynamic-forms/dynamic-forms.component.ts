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
import { isEqual } from 'lodash';
import { debounceTime, takeUntil } from 'rxjs/operators';

import { NzCheckboxOption } from 'ng-zorro-antd/checkbox';

import { DynamicForms, DynamicFormsItem, DynamicFormsType, DynamicFormParams, FormValue } from '@zeppelin/sdk';

const canonicalFormTypes: Record<DynamicFormsType, DynamicFormsType> = {
  TextBox: DynamicFormsType.TextBox,
  Password: DynamicFormsType.Password,
  Select: DynamicFormsType.Select,
  CheckBox: DynamicFormsType.CheckBox,
  input: DynamicFormsType.TextBox,
  select: DynamicFormsType.Select,
  checkbox: DynamicFormsType.CheckBox
};

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
  compareOptionValues = isEqual;
  checkboxGroups: {
    [key: string]: NzCheckboxOption[];
  } = {};
  checkboxValues: {
    [key: string]: number[];
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
    this.checkboxValues = {};
    this.forms.forEach(form => {
      if (this.paramDefs[form.name] === undefined) {
        this.paramDefs[form.name] = form.defaultValue;
      }

      if (this.canonicalFormType(form.type) === DynamicFormsType.CheckBox) {
        const options = form.options ?? [];
        const param = this.paramDefs[form.name];
        const selectedValues = Array.isArray(param) ? param : [];

        // NG-Zorro checkboxes require primitive values. Option indexes preserve
        // Zeppelin's scalar, object and array values at the UI boundary.
        this.checkboxGroups[form.name] = options.map((option, index) => ({
          label: option.displayName || this.optionLabel(option.value),
          value: index
        }));

        this.checkboxValues[form.name] = [];
        options.forEach((option, index) => {
          if (selectedValues.some(value => isEqual(value, option.value))) {
            this.checkboxValues[form.name].push(index);
          }
        });
      }
    });
  }

  canonicalFormType(type: DynamicFormsType): DynamicFormsType {
    return canonicalFormTypes[type];
  }

  optionLabel(value: FormValue): string {
    return typeof value === 'string' ? value : JSON.stringify(value);
  }

  checkboxChange(value: number[], name: string) {
    const options = this.formDefs[name].options ?? [];
    this.paramDefs[name] = value
      .filter(index => index >= 0 && index < options.length)
      .map(index => options[index].value);
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
