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

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  intercept(httpRequest: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    let httpRequestUpdated = httpRequest.clone({ withCredentials: true });
    if (environment.production) {
      httpRequestUpdated = httpRequestUpdated.clone({ setHeaders: { 'X-Requested-With': 'XMLHttpRequest' } });
    }
    return next.handle(httpRequestUpdated).pipe(
      map(event => {
        if (
          event instanceof HttpResponse &&
          !isNil(event.body) &&
          typeof event.body === 'object' &&
          'body' in event.body
        ) {
          return event.clone({ body: event.body.body });
        } else {
          return event;
        }
      }),
      catchError(event => {
        // A 405 from the logout request itself must not start another logout.
        if (!(event.status === 405 && httpRequest.url.includes('logout'))) {
          this.ticketService.handleAuthFailure(event.status, event.headers.get('Location'));
        }
        return throwError(event);
      })
    );
  }
}
