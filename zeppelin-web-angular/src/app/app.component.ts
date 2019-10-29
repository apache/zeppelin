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
