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

import { ChangeDetectionStrategy, Component, Input, OnInit, TemplateRef } from '@angular/core';
import { InputBoolean } from 'ng-zorro-antd/core';

@Component({
  selector: 'zeppelin-page-header',
  templateUrl: './page-header.component.html',
  styleUrls: ['./page-header.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PageHeaderComponent implements OnInit {
  @Input() title: string;
  @Input() description: string | TemplateRef<void>;
  @Input() @InputBoolean() divider = false;
  @Input() extra: TemplateRef<void>;

  constructor() {}

  ngOnInit() {}
}
