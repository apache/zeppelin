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

import { HttpErrorResponse, HttpHandler, HttpRequest } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { TicketService } from '@zeppelin/services';

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
  let interceptor: AppHttpInterceptor;

  /** Drives one request through the interceptor and returns whatever the caller would observe. */
  function intercept(failure: HttpErrorResponse, url = REST_BASE): Promise<unknown> {
    const next: HttpHandler = { handle: () => throwError(() => failure) };
    return new Promise(resolve => {
      interceptor.intercept(new HttpRequest('GET', url), next).subscribe({
        next: resolve,
        error: resolve
      });
    });
  }

  beforeEach(() => {
    logout = vi.fn(() => of({}));
    interceptor = new AppHttpInterceptor({ logout } as unknown as TicketService);
  });

  it('logs out once when a non-logout request is answered with 405', async () => {
    await intercept(sessionExpired(`${REST_BASE}/notebook`), `${REST_BASE}/notebook`);

    // `String.prototype.contains` does not exist, so this branch used to throw before reaching logout
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('rethrows the 405 it logged out on instead of a TypeError', async () => {
    const failure = sessionExpired(`${REST_BASE}/notebook`);

    const observed = await intercept(failure, `${REST_BASE}/notebook`);

    expect(observed).toBe(failure);
  });

  it('does not log out again when the logout request itself is answered with 405', async () => {
    await intercept(sessionExpired(`${REST_BASE}/login/logout`), `${REST_BASE}/login/logout`);

    expect(logout).not.toHaveBeenCalled();
  });

  it('logs out on a 405 that reports no url', async () => {
    // an XHR that never resolved a url cannot be identified as the logout call, so the session is gone
    await intercept(sessionExpired(undefined));

    expect(logout).toHaveBeenCalledTimes(1);
  });
});
