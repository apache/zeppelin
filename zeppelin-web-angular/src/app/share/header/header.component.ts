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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { NzModalService } from 'ng-zorro-antd/modal';

import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { MessageReceiveDataTypeMap, OP } from '@zeppelin/sdk';
import { MessageService, NotebookService, TicketService } from '@zeppelin/services';
import { AboutZeppelinComponent } from '../about-zeppelin/about-zeppelin.component';

@Component({
  selector: 'zeppelin-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HeaderComponent extends MessageListenersManager implements OnInit, OnDestroy {
  private destroy$ = new Subject();
  connectStatus = 'error';
  noteListVisible = false;
  queryStr: string | null = null;
  classicUiHref: string;

  about() {
    this.nzModalService.create({
      nzTitle: 'About Zeppelin',
      nzWidth: '600px',
      nzContent: AboutZeppelinComponent,
      nzFooter: null
    });
  }

  logout() {
    this.ticketService.logout().subscribe();
  }

  onSearch() {
    if (this.queryStr === null) {
      return;
    }
    this.queryStr = this.queryStr.trim();
    if (this.queryStr) {
      this.router.navigate(['/search', this.queryStr]);
    }
  }

  @MessageListener(OP.CONFIGURATIONS_INFO)
  getConfiguration(data: MessageReceiveDataTypeMap[OP.CONFIGURATIONS_INFO]) {
    this.ticketService.setConfiguration(data);
  }

  constructor(
    public ticketService: TicketService,
    public messageService: MessageService,
    private nzModalService: NzModalService,
    private router: Router,
    private notebookService: NotebookService,
    private cdr: ChangeDetectorRef
  ) {
    super(messageService);
    this.classicUiHref = this.resolveClassicUiHref();
  }

  ngOnInit() {
    this.messageService.listConfigurations();
    this.messageService.connectedStatus$.pipe(takeUntil(this.destroy$)).subscribe(status => {
      this.connectStatus = status ? 'success' : 'error';
      this.cdr.markForCheck();
    });
    this.router.events
      .pipe(
        filter(e => e instanceof NavigationEnd),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        this.noteListVisible = false;
        this.cdr.markForCheck();
      });

    this.notebookService
      .queried()
      .pipe(takeUntil(this.destroy$))
      .subscribe(queryStr => (this.queryStr = queryStr));
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    super.ngOnDestroy();
  }

  private resolveClassicUiHref() {
    if (location.pathname === '/') {
      return '/classic';
    } else {
      return '/';
    }
  }
}
