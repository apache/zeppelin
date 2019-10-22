import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';

@Component({
  selector: 'zeppelin-notebook-revisions-comparator',
  templateUrl: './revisions-comparator.component.html',
  styleUrls: ['./revisions-comparator.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookRevisionsComparatorComponent implements OnInit {
  constructor() {}

  ngOnInit() {}
}
