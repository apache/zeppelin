import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output } from '@angular/core';

@Component({
  selector: 'zeppelin-notebook-add-paragraph',
  templateUrl: './add-paragraph.component.html',
  styleUrls: ['./add-paragraph.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookAddParagraphComponent implements OnInit {
  @Output() readonly addParagraph = new EventEmitter();
  @Input() disabled = false;

  clickAdd() {
    if (!this.disabled) {
      this.addParagraph.emit();
    }
  }

  constructor() {}

  ngOnInit() {}
}
