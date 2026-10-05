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

import { HttpClient, HttpErrorResponse, HttpEvent, HttpHandler, HttpRequest } from '@angular/common/http';
import { Router } from '@angular/router';
import { defer, firstValueFrom, of, Subject, throwError } from 'rxjs';

import { NzMessageService } from 'ng-zorro-antd/message';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { BaseUrlService, TicketService } from '@zeppelin/services';

import { AppHttpInterceptor } from './app-http.interceptor';

const REST_BASE = 'http://localhost:8080/api';

/**
 * The server answers an expired session with 405 on the REST base rather than 401, so the 405
 * branch is the only path that reaches logout. The response carries no Location header, which
 * is what keeps the 401 branch out of the way.
 */
function sessionExpired(url: string | undefined): HttpErrorResponse {
  return new HttpErrorResponse({ status: 405, url });
}

describe('AppHttpInterceptor', () => {
  let logout: ReturnType<typeof vi.fn>;
  let logoutSubscribed: ReturnType<typeof vi.fn<() => void>>;
  let interceptor: AppHttpInterceptor;

  /** Drives one request through the interceptor and returns whatever the caller would observe. */
  function intercept(failure: HttpErrorResponse, url = REST_BASE, method = 'GET'): Promise<unknown> {
    const next: HttpHandler = { handle: () => throwError(() => failure) };
    return firstValueFrom(interceptor.intercept(new HttpRequest(method, url, null), next));
  }

  beforeEach(() => {
    logoutSubscribed = vi.fn<() => void>();
    logout = vi.fn(() =>
      defer(() => {
        logoutSubscribed();
        return of({});
      })
    );
    interceptor = new AppHttpInterceptor({ logout } as unknown as TicketService);
  });

  it('logs out once when a non-logout request is answered with 405', async () => {
    const failure = sessionExpired(`${REST_BASE}/notebook`);

    await expect(intercept(failure, `${REST_BASE}/notebook`)).rejects.toBe(failure);

    // `String.prototype.contains` does not exist, so this branch used to throw before reaching logout
    expect(logout).toHaveBeenCalledTimes(1);
    expect(logoutSubscribed).toHaveBeenCalledTimes(1);
  });

  it('rethrows the 405 it logged out on instead of a TypeError', async () => {
    const failure = sessionExpired(`${REST_BASE}/notebook`);

    await expect(intercept(failure, `${REST_BASE}/notebook`)).rejects.toBe(failure);
  });

  it.each([
    ['the logout URL', `${REST_BASE}/login/logout`],
    ['the redirected login URL', `${REST_BASE}/login`],
    ['no URL', undefined]
  ])('does not retry logout when its 405 response reports %s', async (_description, responseUrl) => {
    const failure = sessionExpired(responseUrl);

    await expect(intercept(failure, `${REST_BASE}/login/logout`, 'POST')).rejects.toBe(failure);

    expect(logout).not.toHaveBeenCalled();
    expect(logoutSubscribed).not.toHaveBeenCalled();
  });

  it('logs out on a non-logout request whose 405 reports no url', async () => {
    const failure = sessionExpired(undefined);

    await expect(intercept(failure, `${REST_BASE}/notebook`)).rejects.toBe(failure);

    expect(logout).toHaveBeenCalledTimes(1);
    expect(logoutSubscribed).toHaveBeenCalledTimes(1);
  });

  it('finishes ticket cleanup without another request when logout redirects to a login 405', async () => {
    const requests: Array<{ url: string; response: Subject<HttpEvent<unknown>> }> = [];
    const backend: HttpHandler = {
      handle: request => {
        const response = new Subject<HttpEvent<unknown>>();
        requests.push({ url: request.url, response });
        return response;
      }
    };
    const client = new HttpClient({ handle: request => interceptor.intercept(request, backend) });
    const navigate = vi.fn(() => Promise.resolve(true));
    const service = new TicketService(
      client,
      { getRestApiBase: () => REST_BASE } as BaseUrlService,
      { navigate } as unknown as Router,
      { success: vi.fn() } as unknown as NzMessageService
    );
    service.ticket.init = true;
    service.ticket.principal = 'user1';
    interceptor = new AppHttpInterceptor(service);
    const failure = sessionExpired(`${REST_BASE}/login`);
    const result = firstValueFrom(client.get(`${REST_BASE}/notebook`));

    requests[0].response.error(failure);
    await expect(result).rejects.toBe(failure);
    expect(service.logout$.value).toBe(true);
    expect(requests.map(request => request.url)).toEqual([`${REST_BASE}/notebook`, `${REST_BASE}/login/logout`]);

    requests[1].response.error(sessionExpired(`${REST_BASE}/login`));

    expect(requests.map(request => request.url)).toEqual([`${REST_BASE}/notebook`, `${REST_BASE}/login/logout`]);
    expect(service.ticket.init).toBe(false);
    expect(service.ticket.principal).toBe('');
    expect(service.logout$.value).toBe(false);
    expect(navigate).toHaveBeenCalledExactlyOnceWith(['/login']);
  });

  it.each(['complete', 'error'])('deduplicates pending logout and allows another after %s', async outcome => {
    const pendingLogout = new Subject<object>();
    logout.mockImplementationOnce(() =>
      defer(() => {
        logoutSubscribed();
        return pendingLogout;
      })
    );
    const firstFailure = sessionExpired(`${REST_BASE}/notebook`);
    const secondFailure = sessionExpired(`${REST_BASE}/interpreter`);

    await expect(intercept(firstFailure, `${REST_BASE}/notebook`)).rejects.toBe(firstFailure);
    await expect(intercept(secondFailure, `${REST_BASE}/interpreter`)).rejects.toBe(secondFailure);

    expect(logout).toHaveBeenCalledTimes(1);
    expect(logoutSubscribed).toHaveBeenCalledTimes(1);

    if (outcome === 'error') {
      pendingLogout.error(sessionExpired(`${REST_BASE}/login`));
    } else {
      pendingLogout.complete();
    }

    await expect(intercept(firstFailure, `${REST_BASE}/notebook`)).rejects.toBe(firstFailure);
    expect(logout).toHaveBeenCalledTimes(2);
    expect(logoutSubscribed).toHaveBeenCalledTimes(2);
  });
});
