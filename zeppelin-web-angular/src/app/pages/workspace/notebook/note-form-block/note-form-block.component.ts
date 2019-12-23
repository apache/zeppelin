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
