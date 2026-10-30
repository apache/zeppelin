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

import {
  HttpClient,
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpHeaders,
  HttpRequest,
  HttpResponse
} from '@angular/common/http';
import { Router } from '@angular/router';
import { defer, firstValueFrom, of, Subject, throwError } from 'rxjs';

import { NzMessageService } from 'ng-zorro-antd/message';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { BaseUrlService, TicketService } from '@zeppelin/services';

import sessionExpiredCapture from '../../e2e/fixtures/notebook-auth/http-session-expired.json?raw';
import logoutCapture from '../../e2e/fixtures/notebook-auth/rest-401-logout.json?raw';
import { AppHttpInterceptor } from './app-http.interceptor';

interface CapturedRest {
  kind: string;
  requestId?: string;
  redirectedFrom?: string;
  rest?: {
    direction: string;
    status?: number;
    headers?: Record<string, string>;
    request: { method: string; url: string };
  };
}

const capturedRecords = (raw: string): CapturedRest[] => (JSON.parse(raw) as { records: CapturedRest[] }).records;

/** Each captured 405 together with the request the browser was redirected from, which is what Angular sent. */
const captured405s = (raw: string) => {
  const records = capturedRecords(raw);
  const request = (requestId?: string) =>
    records.find(record => record.rest?.direction === 'request' && record.requestId === requestId);
  return records
    .filter(record => record.rest?.direction === 'response' && record.rest.status === 405)
    .map(response => request(request(response.requestId)?.redirectedFrom)!.rest!.request);
};

const REST_BASE = 'http://localhost:8080/api';

const createBaseUrl = (): BaseUrlService => {
  const baseUrl = new BaseUrlService();
  vi.spyOn(baseUrl, 'getRestApiBase').mockReturnValue(REST_BASE);
  return baseUrl;
};

/**
 * The server answers an expired session with 405 on the REST base rather than 401, so the 405
 * branch is the only path that reaches logout. The response carries no Location header, which
 * is what keeps the 401 branch out of the way.
 */
function sessionExpired(url: string | undefined): HttpErrorResponse {
  return new HttpErrorResponse({ status: 405, url });
}

describe('AppHttpInterceptor', () => {
  let logout: ReturnType<typeof vi.fn<TicketService['logout']>>;
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
    const client = new HttpClient({ handle: () => throwError(() => new Error('Unexpected HTTP request')) });
    const service = new TicketService(client, createBaseUrl(), {} as Router, {} as NzMessageService);
    // The real auth-failure policy, with only the logout request stubbed.
    vi.spyOn(service, 'logout').mockImplementation(logout);
    interceptor = new AppHttpInterceptor(service);
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
      createBaseUrl(),
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

  it('logs out once for each captured 405 on a request that is not the logout itself', async () => {
    const sources = captured405s(sessionExpiredCapture);
    const fromLogout = sources.filter(source => source.url === '/api/login/logout');
    expect(fromLogout.length).toBeGreaterThan(0);
    expect(sources.length - fromLogout.length).toBeGreaterThan(0);

    for (const source of sources) {
      const failure = sessionExpired(`${REST_BASE}/login`);
      await expect(intercept(failure, `${REST_BASE}${source.url.replace(/^\/api/, '')}`, source.method)).rejects.toBe(
        failure
      );
    }

    expect(logout).toHaveBeenCalledTimes(sources.length - fromLogout.length);
  });

  describe('a REST 401', () => {
    // jsdom ignores navigation to anything but a hash, so record the assignments instead.
    let navigations: string[];

    beforeEach(() => {
      navigations = [];
      vi.spyOn(window, 'location', 'get').mockReturnValue({
        set href(value: string) {
          navigations.push(value);
        }
      } as Location);
    });

    afterEach(() => vi.restoreAllMocks());

    it('stays on the page for the captured logout 401, which has no Location', async () => {
      const response = capturedRecords(logoutCapture).find(record => record.rest?.status === 401)!.rest!;
      const failure = new HttpErrorResponse({
        status: 401,
        url: `${REST_BASE}/login/logout`,
        headers: new HttpHeaders(response.headers)
      });

      await expect(intercept(failure, `${REST_BASE}/login/logout`, 'POST')).rejects.toBe(failure);

      expect(navigations).toEqual([]);
      expect(logout).not.toHaveBeenCalled();
    });

    // Synthetic: the captured server never sends a 401 with Location; that needs an SSO realm such as Knox.
    it('follows the Location of a synthetic 401 that has one', async () => {
      const failure = new HttpErrorResponse({
        status: 401,
        url: `${REST_BASE}/notebook`,
        headers: new HttpHeaders({ Location: '/sso/login' })
      });

      await expect(intercept(failure, `${REST_BASE}/notebook`)).rejects.toBe(failure);

      expect(navigations).toEqual(['/sso/login']);
      expect(logout).not.toHaveBeenCalled();
    });
  });
});

describe('Ticket authentication checks through AppHttpInterceptor', () => {
  let service: TicketService;
  let failure: HttpErrorResponse;
  let requestedUrls: string[];
  let navigate: ReturnType<typeof vi.fn>;
  let redirects: string[];

  beforeEach(() => {
    failure = sessionExpired(`${REST_BASE}/login`);
    requestedUrls = [];
    redirects = [];
    navigate = vi.fn(() => Promise.resolve(true));
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      set href(value: string) {
        redirects.push(value);
      }
    } as Location);
    let interceptor: AppHttpInterceptor;
    const backend: HttpHandler = {
      handle: request => {
        requestedUrls.push(request.url);
        if (request.url === `${REST_BASE}/version`) {
          return of(new HttpResponse({ body: { body: { version: 'test' } } }));
        }
        return throwError(() => failure);
      }
    };
    const client = new HttpClient({ handle: request => interceptor.intercept(request, backend) });
    service = new TicketService(
      client,
      createBaseUrl(),
      { navigate } as unknown as Router,
      { success: vi.fn() } as unknown as NzMessageService
    );
    interceptor = new AppHttpInterceptor(service);
  });

  afterEach(() => vi.restoreAllMocks());

  it('leaves an initial unauthenticated check to its guard without logging out', async () => {
    await expect(firstValueFrom(service.getTicket())).rejects.toBe(failure);

    expect(requestedUrls).not.toContain(`${REST_BASE}/login/logout`);
    expect(navigate).not.toHaveBeenCalled();
    expect(service.ticket.init).toBe(false);
    expect(service.logout$.value).toBe(false);
  });

  it('still clears an initialized session when its ticket check fails with 405', async () => {
    service.ticket.init = true;
    service.ticket.principal = 'user1';

    await expect(firstValueFrom(service.getTicket())).rejects.toBe(failure);

    expect(requestedUrls.filter(url => url === `${REST_BASE}/login/logout`)).toHaveLength(1);
    expect(navigate).toHaveBeenCalledExactlyOnceWith(['/login']);
    expect(service.ticket.init).toBe(false);
    expect(service.ticket.principal).toBe('');
    expect(service.logout$.value).toBe(false);
  });

  it('still follows an SSO 401 Location during an initial authentication check', async () => {
    failure = new HttpErrorResponse({
      status: 401,
      url: `${REST_BASE}/security/ticket`,
      headers: new HttpHeaders({ Location: '/sso/login' })
    });

    await expect(firstValueFrom(service.isAuthenticated())).rejects.toBe(failure);

    expect(redirects).toEqual(['/sso/login']);
    expect(requestedUrls).not.toContain(`${REST_BASE}/login/logout`);
    expect(navigate).not.toHaveBeenCalled();
  });
});
