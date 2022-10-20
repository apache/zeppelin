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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, Output } from '@angular/core';

import { DestroyHookComponent } from '@zeppelin/core';
import { SecurityService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-credential-permission-select',
  templateUrl: './permissions.component.html',
  styleUrls: ['./permissions.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CredentialPermissionSelectComponent extends DestroyHookComponent {
  @Input() reference: string;
  @Input() permission: { items: string[] };
  @Input() mode: 'create' | 'view' | 'edit' = 'view';
  @Output() readonly permissionsBack = new EventEmitter<string[]>();
  listOfUserAndRole = [];

  searchUser(search: string) {
    if (!search) {
      return;
    }
    this.securityService.searchUsers(search).subscribe(data => {
      const results = [];
      if (data.users.length) {
        results.push({
          text: 'Users :',
          children: data.users
        });
      }
      if (data.roles.length) {
        results.push({
          text: 'Roles :',
          children: data.roles
        });
      }
      this.listOfUserAndRole = results;
      this.cdr.markForCheck();
    });
  }

  onChange() {
    this.permissionsBack.emit(this.permission.items);
  }

  constructor(private securityService: SecurityService, private cdr: ChangeDetectorRef) {
    super();
  }
}
