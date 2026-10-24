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

import { Router } from '@angular/router';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NzNotificationService } from 'ng-zorro-antd/notification';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { MessageReceiveDataTypeMap, WebSocketMessage } from '@zeppelin/sdk';
import { TicketService } from '@zeppelin/services';

import authInfoCapture from '../../e2e/fixtures/notebook-auth/ws-global-auth-info.json?raw';
import errorInfoCapture from '../../e2e/fixtures/notebook-auth/ws-global-error-info.json?raw';
import sessionLogoutCapture from '../../e2e/fixtures/notebook-auth/ws-session-logout.json?raw';
import { AppMessageInterceptor } from './app-message.interceptor';

type Message = WebSocketMessage<MessageReceiveDataTypeMap>;

/** The first frame of `op` the server sent in a captured fixture. */
const capturedFrame = (raw: string, op: string): Message => {
  const { records } = JSON.parse(raw) as {
    records: Array<{ kind: string; websocket?: { direction: string; payloadText: string } }>;
  };
  const frame = records
    .filter(record => record.kind === 'websocket' && record.websocket!.direction === 'receive')
    .map(record => JSON.parse(record.websocket!.payloadText) as Message)
    .find(message => message.op === op);
  if (!frame) throw new Error(`No captured ${op}`);
  return frame;
};

describe('AppMessageInterceptor with captured shell messages', () => {
  let navigate: ReturnType<typeof vi.fn>;
  let warning: ReturnType<typeof vi.fn>;
  let create: ReturnType<typeof vi.fn>;
  let confirm: ReturnType<typeof vi.fn>;
  let ticketService: TicketService;

  const interceptor = () =>
    new AppMessageInterceptor(
      { navigate } as unknown as Router,
      { warning } as unknown as NzNotificationService,
      ticketService,
      { create, confirm } as unknown as NzModalService
    );

  beforeEach(() => {
    navigate = vi.fn(() => Promise.resolve(true));
    warning = vi.fn();
    create = vi.fn();
    confirm = vi.fn();
    ticketService = { ticket: { principal: 'signed-in', ticket: 'kept', roles: '["role3"]' } } as TicketService;
  });

  it('presents a captured AUTH_INFO without changing the authentication state', () => {
    const before = structuredClone(ticketService.ticket);
    const message = capturedFrame(authInfoCapture, 'AUTH_INFO');

    expect(interceptor().received(message)).toBe(message);

    expect(create).toHaveBeenCalledOnce();
    expect(create.mock.calls[0][0]).toMatchObject({ nzTitle: 'Insufficient privileges' });
    expect(confirm).not.toHaveBeenCalled();
    expect(ticketService.ticket).toEqual(before);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('presents a captured AUTH_INFO to an anonymous ticket as a plain confirmation', () => {
    ticketService.ticket.roles = '[]';

    interceptor().received(capturedFrame(authInfoCapture, 'AUTH_INFO'));

    expect(confirm).toHaveBeenCalledOnce();
    expect(create).not.toHaveBeenCalled();
  });

  it('shows a captured global ERROR_INFO as a host notification', () => {
    const message = capturedFrame(errorInfoCapture, 'ERROR_INFO');

    interceptor().received(message);

    expect(warning).toHaveBeenCalledExactlyOnceWith(
      'ERROR',
      (message.data as { info: string }).info,
      expect.anything()
    );
    expect(create).not.toHaveBeenCalled();
  });

  it('leaves a captured SESSION_LOGOUT unhandled while the same interceptor handles AUTH_INFO', () => {
    const before = structuredClone(ticketService.ticket);
    const host = interceptor();
    const logout = capturedFrame(sessionLogoutCapture, 'SESSION_LOGOUT');

    expect(host.received(logout)).toBe(logout);
    expect([create, confirm, warning, navigate].map(spy => spy.mock.calls.length)).toEqual([0, 0, 0, 0]);
    expect(ticketService.ticket).toEqual(before);

    host.received(capturedFrame(authInfoCapture, 'AUTH_INFO'));
    expect(create).toHaveBeenCalledOnce();
  });
});
