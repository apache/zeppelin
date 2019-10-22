import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

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

  login() {
    this.loading = true;
    this.ticketService.login(this.userName, this.password).subscribe(
      () => {
        this.loading = false;
        this.cdr.markForCheck();
        this.router.navigate(['/']).then();
      },
      () => {
        this.loading = false;
        this.cdr.markForCheck();
      }
    );
  }

  constructor(private ticketService: TicketService, private cdr: ChangeDetectorRef, private router: Router) {}

  ngOnInit() {}
}
