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
import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { DynamicForms, DynamicFormParams } from '@zeppelin/sdk';

@Component({
  selector: 'zeppelin-note-form-block',
  templateUrl: './note-form-block.component.html',
  styleUrls: ['./note-form-block.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NoteFormBlockComponent implements OnInit {
  @Input() noteTitle: string;
  @Input() formDefs: DynamicForms;
  @Input() paramDefs: DynamicFormParams;
  @Output() readonly noteTitleChange = new EventEmitter<string>();
  @Output() readonly noteFormChange = new EventEmitter<DynamicFormParams>();
  @Output() readonly noteFormNameRemove = new EventEmitter<string>();
  constructor() {}

  ngOnInit() {}

  onFormRemove({ name }) {
    this.noteFormNameRemove.emit(name);
  }

  onFormChange() {
    this.noteFormChange.emit(this.paramDefs);
  }

  setTitle(title: string) {
    this.noteTitleChange.emit(title);
  }
}
