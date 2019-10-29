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

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { OP } from '@zeppelin/sdk';
import { MessageService, TicketService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HomeComponent extends MessageListenersManager implements OnInit {
  loading = false;

  reloadNoteList() {
    this.messageService.reloadAllNotesFromRepo();
    this.loading = true;
  }

  @MessageListener(OP.NOTES_INFO)
  getNotes() {
    this.loading = false;
    this.cdr.markForCheck();
  }

  constructor(
    public ticketService: TicketService,
    public messageService: MessageService,
    private cdr: ChangeDetectorRef
  ) {
    super(messageService);
  }

  ngOnInit() {}
}
