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
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ConfigurationService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfigurationComponent implements OnInit {
  configEntries: Array<[string, string]> = [];

  constructor(private configurationService: ConfigurationService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.getAllConfig();
  }

  getAllConfig(): void {
    this.configurationService.getAll().subscribe(data => {
      this.configEntries = [...Object.entries<string>(data)];
      this.cdr.markForCheck();
    });
  }
}
