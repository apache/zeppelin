import { Component } from '@angular/core';
import { NavigationEnd, NavigationStart, Router } from '@angular/router';
import { filter, map } from 'rxjs/operators';

import { TicketService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.less']
})
export class AppComponent {
  logout$ = this.ticketService.logout$;
  loading$ = this.router.events.pipe(
    filter(data => data instanceof NavigationEnd || data instanceof NavigationStart),
    map(data => {
      if (data instanceof NavigationStart) {
        // load ticket when redirect to workspace
        return data.url === '/';
      } else if (data instanceof NavigationEnd) {
        return false;
      }
    })
  );

  constructor(private router: Router, private ticketService: TicketService) {}
}
