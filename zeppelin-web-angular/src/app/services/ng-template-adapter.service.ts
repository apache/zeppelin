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

import { Injectable } from '@angular/core';
import { Ng1MigrationComponent } from '@zeppelin/share/ng1-migration/ng1-migration.component';
import { NzModalService } from 'ng-zorro-antd';
import { Observable } from 'rxjs';

export interface NgTemplateCheckResult {
  index: number;
  match: string;
  magic: string;
  template: string;
  origin: string;
}

@Injectable({
  providedIn: 'root'
})
export class NgTemplateAdapterService {
  constructor(private nzModalService: NzModalService) {}
  preCheck(origin: string): NgTemplateCheckResult | null {
    const regexp = /(%angular)([\s\S]*<[\s\S]*>)/im;
    const math = regexp.exec(origin);
    if (math) {
      const index = math.index;
      const [output, magic, template] = math;
      return {
        index,
        magic,
        template,
        origin,
        match: output
      };
    }
    return null;
  }

  openMigrationDialog(check: NgTemplateCheckResult): Observable<string> {
    const modalRef = this.nzModalService.create({
      nzTitle: 'Angular.js Templates Migration Tool',
      nzContent: Ng1MigrationComponent,
      nzComponentParams: check,
      nzFooter: null,
      nzWidth: '980px',
      nzStyle: {
        top: '45px'
      },
      nzBodyStyle: {
        padding: '0'
      }
    });

    return modalRef.afterClose;
  }
}
