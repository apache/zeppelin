import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { TableData, Visualization, VISUALIZATION } from '@zeppelin/visualization';

@Component({
  selector: 'lib-helium-vis-example',
  template: `
    <pre><code>{{tableData | json}}</code></pre>
  `,
  styles: [`
    pre {
      background: #fff7e7;
      padding: 10px;
      border: 1px solid #ffd278;
      color: #fa7e14;
      border-radius: 3px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JsonVisComponent implements OnInit {
  tableData: TableData;
  constructor(@Inject(VISUALIZATION) public visualization: Visualization, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
  }

  render(): void {
    this.tableData = this.visualization.transformed;
  }

}
