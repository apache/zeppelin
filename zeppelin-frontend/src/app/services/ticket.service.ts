import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, BehaviorSubject, Subject } from 'rxjs';
import { map, tap } from 'rxjs/operators';

import { NzMessageService } from 'ng-zorro-antd';

import { ITicket, ITicketWrapped, IZeppelinVersion } from '@zeppelin/interfaces';
import { ConfigurationsInfo } from '@zeppelin/sdk';

import { BaseUrlService } from './base-url.service';

@Injectable({
  providedIn: 'root'
})
export class TicketService {
  configuration: ConfigurationsInfo['configurations'];
  ticket = new ITicketWrapped();
  originTicket = new ITicket();
  ticket$ = new Subject<ITicketWrapped>();
  logout$ = new BehaviorSubject<boolean>(false);
  version: string;

  setConfiguration(conf: ConfigurationsInfo) {
    this.configuration = conf.configurations;
  }

  getTicket() {
    return forkJoin([
      this.httpClient.get<ITicket>(`${this.baseUrlService.getRestApiBase()}/security/ticket`),
      this.getZeppelinVersion()
    ]).pipe(
      tap(data => {
        const [ticket, version] = data;
        this.version = version;
        this.setTicket(ticket);
      })
    );
  }

  setTicket(ticket: ITicket) {
    if (ticket.redirectURL) {
      window.location.href = ticket.redirectURL + window.location.href;
    }
    let screenUsername = ticket.principal;
    if (ticket.principal.indexOf('#Pac4j') === 0) {
      const re = ', name=(.*?),';
      screenUsername = ticket.principal.match(re)[1];
    }
    this.originTicket = ticket;
    this.ticket = { ...ticket, screenUsername, ...{ init: true } };
    this.ticket$.next(this.ticket);
  }

  clearTicket() {
    this.ticket = new ITicketWrapped();
    this.originTicket = new ITicket();
  }

  logout() {
    this.logout$.next(true);
    const nextAction = () => {
      this.nzMessageService.success('Logout Success');
      this.clearTicket();
      this.logout$.next(false);
      this.router.navigate(['/login']).then();
    };
    return this.httpClient
      .post(`${this.baseUrlService.getRestApiBase()}/login/logout`, {})
      .pipe(tap(() => nextAction(), () => nextAction()));
  }

  login(userName: string, password: string) {
    return this.httpClient
      .post<ITicket>(`${this.baseUrlService.getRestApiBase()}/login`, `password=${password}&userName=${userName}`, {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
      })
      .pipe(
        tap(
          data => {
            this.nzMessageService.success('Login Success');
            this.setTicket(data);
          },
          () => {
            this.nzMessageService.warning("The username and password that you entered don't match.");
          }
        )
      );
  }

  getZeppelinVersion() {
    return this.httpClient
      .get<IZeppelinVersion>(`${this.baseUrlService.getRestApiBase()}/version`)
      .pipe(map(data => data.version));
  }

  constructor(
    private httpClient: HttpClient,
    private baseUrlService: BaseUrlService,
    private router: Router,
    private nzMessageService: NzMessageService
  ) {}
}
