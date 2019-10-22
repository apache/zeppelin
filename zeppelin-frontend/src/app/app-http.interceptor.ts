import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { throwError, Observable } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { isNil } from 'lodash';

import { environment } from '@zeppelin/environment';
import { TicketService } from '@zeppelin/services';

@Injectable()
export class AppHttpInterceptor implements HttpInterceptor {
  constructor(private ticketService: TicketService) {}

  // tslint:disable-next-line:no-any
  intercept(httpRequest: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    let httpRequestUpdated = httpRequest.clone({ withCredentials: true });
    if (environment.production) {
      httpRequestUpdated = httpRequest.clone({ setHeaders: { 'X-Requested-With': 'XMLHttpRequest' } });
    }
    return next.handle(httpRequestUpdated).pipe(
      map(event => {
        if (event instanceof HttpResponse) {
          return event.clone({ body: event.body.body });
        } else {
          return event;
        }
      }),
      catchError(event => {
        const redirect = event.headers.get('Location');
        if (event.status === 401 && !isNil(redirect)) {
          // Handle page redirect
          window.location.href = redirect;
        } else if (event.status === 405 && !event.url.contains('logout')) {
          this.ticketService.logout().subscribe();
        }
        return throwError(event);
      })
    );
  }
}
