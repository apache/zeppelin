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
