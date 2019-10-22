import { ChangeDetectionStrategy, Component, Input, OnInit, TemplateRef } from '@angular/core';
import { InputBoolean } from 'ng-zorro-antd';

@Component({
  selector: 'zeppelin-page-header',
  templateUrl: './page-header.component.html',
  styleUrls: ['./page-header.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PageHeaderComponent implements OnInit {
  @Input() title: string;
  @Input() description: string;
  @Input() @InputBoolean() divider = false;
  @Input() extra: TemplateRef<void>;

  constructor() {}

  ngOnInit() {}
}
