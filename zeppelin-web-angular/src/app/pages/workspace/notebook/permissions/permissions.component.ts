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

import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output
} from '@angular/core';

import { NzMessageService, NzModalService } from 'ng-zorro-antd';

import { Permissions } from '@zeppelin/interfaces';
import { SecurityService, TicketService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-notebook-permissions',
  templateUrl: './permissions.component.html',
  styleUrls: ['./permissions.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookPermissionsComponent implements OnInit, OnChanges {
  @Input() permissions: Permissions;
  @Input() noteId: string;
  @Input() activatedExtension: 'interpreter' | 'permissions' | 'revisions' | 'hide' = 'hide';
  @Output() readonly activatedExtensionChange = new EventEmitter<
    'interpreter' | 'permissions' | 'revisions' | 'hide'
  >();
  permissionsBack: Permissions;
  listOfUserAndRole = [];

  savePermissions() {
    const principal = this.ticketService.ticket.principal;
    const isAnonymous = principal === 'anonymous';
    if (isAnonymous || this.ticketService.ticket.principal.trim().length === 0) {
      this.blockAnonUsers();
    }
    if (this.isOwnerEmpty()) {
      this.nzModalService.create({
        nzTitle: 'Setting Owners Permissions',
        nzContent: `Please fill the [Owners] field. If not, it will set as current user. Current user : [ ${this.ticketService.ticket.principal.trim()} ]`,
        nzOnOk: () => {
          this.permissions.owners = [this.ticketService.ticket.principal];
          this.setPermissions();
        },
        nzOnCancel: () => {
          this.resetPermissions();
        }
      });
    } else {
      this.setPermissions();
    }
  }

  closePermissions() {
    this.activatedExtension = 'hide';
    this.activatedExtensionChange.emit('hide');
  }

  blockAnonUsers() {
    this.nzModalService.create({
      nzTitle: 'No permission',
      nzContent: 'Only authenticated user can set the permission.',
      nzOkText: 'Read Doc',
      nzOnOk: () => {
        const url = `https://zeppelin.apache.org/docs/${this.ticketService.version}/security/notebook_authorization.html`;
        window.open(url);
      }
    });
  }

  setPermissions() {
    this.securityService.setPermissions(this.noteId, this.permissions).subscribe(() => {
      this.nzMessageService.success('Permissions Saved Successfully');
      this.closePermissions();
    });
  }

  resetPermissions() {
    this.permissions = { ...this.permissionsBack };
  }

  isOwnerEmpty() {
    return !this.permissions.owners.some(o => o.trim().length > 0);
  }

  searchUser(search: string) {
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

  constructor(
    private securityService: SecurityService,
    private cdr: ChangeDetectorRef,
    private nzMessageService: NzMessageService,
    private ticketService: TicketService,
    private nzModalService: NzModalService
  ) {}

  ngOnInit() {
    this.permissionsBack = { ...this.permissions };
  }

  ngOnChanges(): void {
    this.permissionsBack = { ...this.permissions };
  }
}
