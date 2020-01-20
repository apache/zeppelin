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
