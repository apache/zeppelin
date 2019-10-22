import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { TicketService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-about-zeppelin',
  templateUrl: './about-zeppelin.component.html',
  styleUrls: ['./about-zeppelin.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AboutZeppelinComponent implements OnInit {
  constructor(public ticketService: TicketService) {}

  ngOnInit() {}
}
