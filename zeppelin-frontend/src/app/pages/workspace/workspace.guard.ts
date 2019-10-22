import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { of, Observable } from 'rxjs';
import { catchError, mapTo, tap } from 'rxjs/operators';

import { MessageService, TicketService } from '@zeppelin/services';

@Injectable({
  providedIn: 'root'
})
export class WorkspaceGuard implements CanActivate {
  constructor(private ticketService: TicketService, private router: Router, private messageService: MessageService) {}

  canActivate(
    next: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    return this.ticketService.getTicket().pipe(
      mapTo(true),
      tap(() => this.messageService.bootstrap()),
      catchError(() => of(this.router.createUrlTree(['/login'])))
    );
  }
}
