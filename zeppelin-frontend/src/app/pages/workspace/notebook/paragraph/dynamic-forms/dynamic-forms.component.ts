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

import { NzCheckBoxOptionInterface } from 'ng-zorro-antd';

import { DynamicForms, DynamicFormsItem, DynamicFormsType, DynamicFormParams } from '@zeppelin/sdk';

@Component({
  selector: 'zeppelin-notebook-paragraph-dynamic-forms',
  templateUrl: './dynamic-forms.component.html',
  styleUrls: ['./dynamic-forms.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookParagraphDynamicFormsComponent implements OnInit, OnChanges, OnDestroy {
  private destroy$ = new Subject();

  @Input() formDefs: DynamicForms;
  @Input() paramDefs: DynamicFormParams;
  @Input() runOnChange = false;
  @Input() disable = false;
  @Output() readonly formChange = new EventEmitter<void>();

  formChange$ = new Subject<void>();
  forms: DynamicFormsItem[] = [];
  formType = DynamicFormsType;
  checkboxGroups: {
    [key: string]: NzCheckBoxOptionInterface[];
  } = {};

  @HostListener('keydown.enter')
  onEnter() {
    if (!this.runOnChange) {
      this.formChange.emit();
    }
  }

  trackByNameFn(_index, form: DynamicFormsItem) {
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
        this.checkboxGroups[e.name] = e.options.map(opt => {
          let checked = false;
          if (this.paramDefs[e.name] && Array.isArray(this.paramDefs[e.name])) {
            const param = this.paramDefs[e.name] as string[];
            checked = param.indexOf(opt.value) !== -1;
          }
          return {
            checked,
            label: opt.displayName || opt.value,
            value: opt.value
          };
        });
      }
    });
  }

  checkboxChange(value: NzCheckBoxOptionInterface[], name) {
    this.paramDefs[name] = value.filter(e => e.checked).map(e => e.value);
    this.onFormChange();
  }

  onFormChange() {
    if (this.runOnChange) {
      this.formChange$.next();
    }
  }

  constructor() {}

  ngOnInit() {
    this.setForms();
    this.formChange$
      .pipe(
        debounceTime(800),
        takeUntil(this.destroy$)
      )
      .subscribe(() => this.formChange.emit());
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.setForms();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
