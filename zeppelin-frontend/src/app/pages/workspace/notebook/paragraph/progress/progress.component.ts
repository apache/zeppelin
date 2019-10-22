import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';

@Component({
  selector: 'zeppelin-notebook-paragraph-progress',
  templateUrl: './progress.component.html',
  styleUrls: ['./progress.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookParagraphProgressComponent implements OnChanges {
  @Input() progress = 0;
  displayProgress = 0;

  ngOnChanges(): void {
    if (this.progress > 0 && this.progress < 100) {
      this.displayProgress = this.progress;
    } else {
      this.displayProgress = 100;
    }
  }
}
