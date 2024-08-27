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
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges
} from '@angular/core';
import { FormArray, FormBuilder, Validators } from '@angular/forms';
import { NotebookRepo } from '@zeppelin/interfaces';

@Component({
  selector: 'zeppelin-notebook-repo-item',
  templateUrl: './item.component.html',
  styleUrls: ['./item.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookRepoItemComponent implements OnChanges {
  @Input() repo: NotebookRepo;
  @Output() readonly repoChange = new EventEmitter<NotebookRepo>();

  settingFormArray: FormArray;
  editMode = false;

  constructor(private cdr: ChangeDetectorRef, private fb: FormBuilder) {}

  triggerEditMode() {
    this.editMode = !this.editMode;
    this.cdr.markForCheck();
  }

  save() {
    this.settingFormArray.controls.forEach(control => {
      control.markAsDirty();
      control.updateValueAndValidity();
    });

    if (this.settingFormArray.valid) {
      const values = this.settingFormArray.getRawValue() as string[];
      values.forEach((value, i) => (this.repo.settings[i].selected = value));
      this.repoChange.emit(this.repo);
      this.editMode = false;
      this.cdr.markForCheck();
    }
  }

  cancel() {
    this.buildForm();
    this.editMode = false;
    this.cdr.markForCheck();
  }

  buildForm() {
    const controls = this.repo.settings.map(setting => {
      return this.fb.control(setting.selected, [Validators.required]);
    });
    this.settingFormArray = this.fb.array(controls);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.repo) {
      this.buildForm();
    }
  }
}
