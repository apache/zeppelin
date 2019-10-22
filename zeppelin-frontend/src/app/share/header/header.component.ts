import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { NzModalService } from 'ng-zorro-antd';

import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { MessageReceiveDataTypeMap, OP } from '@zeppelin/sdk';
import { MessageService, TicketService } from '@zeppelin/services';
import { AboutZeppelinComponent } from '@zeppelin/share/about-zeppelin/about-zeppelin.component';

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

  @MessageListener(OP.CONFIGURATIONS_INFO)
  getConfiguration(data: MessageReceiveDataTypeMap[OP.CONFIGURATIONS_INFO]) {
    this.ticketService.setConfiguration(data);
  }

  constructor(
    public ticketService: TicketService,
    private nzModalService: NzModalService,
    public messageService: MessageService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    super(messageService);
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
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    super.ngOnDestroy();
  }
}
