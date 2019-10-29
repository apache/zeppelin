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
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  Renderer2,
  SimpleChanges,
  ViewChild
} from '@angular/core';

@Component({
  selector: 'zeppelin-elastic-input',
  templateUrl: './elastic-input.component.html',
  styleUrls: ['./elastic-input.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ElasticInputComponent implements OnChanges {
  @Input() value: string;
  @Input() readonly = false;
  @Input() min = false;
  @Output() readonly valueUpdate = new EventEmitter<string>();
  @ViewChild('inputElement', { read: ElementRef, static: false }) inputElement: ElementRef;
  @ViewChild('pElement', { read: ElementRef, static: false }) pElement: ElementRef;
  @ViewChild('elasticElement', { read: ElementRef, static: true }) elasticElement: ElementRef;
  showEditor = false;
  editValue: string;

  cancelEdit() {
    this.editValue = this.value;
    this.showEditor = false;
  }

  updateValue(value: string) {
    const trimmedNewName = value.trim();
    if (trimmedNewName.length > 0 && this.value !== trimmedNewName) {
      this.editValue = trimmedNewName;
    }
  }

  setEditorState(showEditor: boolean) {
    if (!this.readonly) {
      this.showEditor = showEditor;
      if (!this.showEditor) {
        this.valueUpdate.emit(this.editValue);
      } else {
        const width = this.pElement.nativeElement.getBoundingClientRect().width;
        this.renderer.setStyle(this.elasticElement.nativeElement, 'width', `${width}px`);
        setTimeout(() => {
          this.inputElement.nativeElement.focus();
          this.renderer.setStyle(this.inputElement.nativeElement, 'width', `${width}px`);
        });
      }
    }
  }

  updateInputWidth() {
    const width = this.inputElement.nativeElement.scrollWidth;
    if (width > this.inputElement.nativeElement.getBoundingClientRect().width) {
      this.renderer.removeStyle(this.elasticElement.nativeElement, 'width');
      this.renderer.setStyle(this.inputElement.nativeElement, 'width', `${width}px`);
    }
  }

  constructor(private renderer: Renderer2) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes.value) {
      this.showEditor = false;
      this.editValue = this.value;
      this.renderer.removeStyle(this.elasticElement.nativeElement, 'width');
    }
  }
}
