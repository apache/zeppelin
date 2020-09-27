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
import { ActivatedRoute, Router } from '@angular/router';

import { TicketService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent implements OnInit {
  userName: string;
  password: string;
  loading = false;
  private returnUrl: string | undefined;

  login() {
    this.loading = true;
    this.ticketService.login(this.userName, this.password).subscribe(
      () => {
        this.loading = false;
        this.cdr.markForCheck();
        this.router.navigateByUrl(this.returnUrl || '/');
      },
      () => {
        this.loading = false;
        this.cdr.markForCheck();
      }
    );
  }

  constructor(
    private ticketService: TicketService,
    private cdr: ChangeDetectorRef,
    private router: Router,
    private route: ActivatedRoute
  ) {
    route.queryParams.subscribe(params => {
      this.returnUrl = params.returnUrl;
    });
  }

  ngOnInit() {}
}
