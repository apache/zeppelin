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
import { Router } from '@angular/router';

import { NzModalService, NzNotificationService } from 'ng-zorro-antd';

import { MessageInterceptor } from '@zeppelin/interfaces';
import { MessageReceiveDataTypeMap, OP, WebSocketMessage } from '@zeppelin/sdk';
import { TicketService } from '@zeppelin/services';

@Injectable()
export class AppMessageInterceptor implements MessageInterceptor {
  constructor(
    private router: Router,
    private nzNotificationService: NzNotificationService,
    private ticketService: TicketService,
    private nzModalService: NzModalService
  ) {}

  received<T extends keyof MessageReceiveDataTypeMap>(data: WebSocketMessage<T>): WebSocketMessage<T> {
    if (data.op === OP.NEW_NOTE) {
      const rData = data.data as MessageReceiveDataTypeMap[OP.NEW_NOTE];
      this.router.navigate(['/notebook', rData.note.id]).then();
    } else if (data.op === OP.AUTH_INFO) {
      const rData = data.data as MessageReceiveDataTypeMap[OP.AUTH_INFO];
      if (this.ticketService.ticket.roles === '[]') {
        this.nzModalService.confirm({
          nzClosable: false,
          nzMaskClosable: false,
          nzTitle: 'Insufficient privileges',
          nzContent: rData.info
        });
      } else {
        this.nzModalService.create({
          nzClosable: false,
          nzMaskClosable: false,
          nzTitle: 'Insufficient privileges',
          nzContent: rData.info,
          nzOkText: 'Login',
          nzOnOk: () => {
            this.router.navigate(['/login']).then();
          },
          nzOnCancel: () => {
            this.router.navigate(['/']).then();
          }
        });
      }
    } else if (data.op === OP.ERROR_INFO) {
      // tslint:disable-next-line:no-any
      const rData = (data.data as any) as MessageReceiveDataTypeMap[OP.ERROR_INFO];
      if (rData.info) {
        this.nzNotificationService.warning('ERROR', rData.info);
      }
    }
    return data;
  }
}
