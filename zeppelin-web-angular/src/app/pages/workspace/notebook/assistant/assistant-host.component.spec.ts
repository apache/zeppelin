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

import { HttpClient, HttpErrorResponse, type HttpEvent, type HttpHandler } from '@angular/common/http';
import { SimpleChange, type ChangeDetectorRef } from '@angular/core';
import type { NzMessageService } from 'ng-zorro-antd/message';
import { BehaviorSubject, firstValueFrom, Subject, throwError } from 'rxjs';
import { expect, it, vi } from 'vitest';
import { OP, type AssistantSocketEvent } from '@zeppelin/sdk';
import { TicketService, type BaseUrlService, type MessageService } from '@zeppelin/services';
import type { Router } from '@angular/router';
import { AppHttpInterceptor } from '../../../../app-http.interceptor';
import { AssistantHostComponent } from './assistant-host.component';
import { AssistantSlots } from './assistant-slots';

const createHost = (initialized = true) => {
  const slots = new AssistantSlots();
  const message = { error: vi.fn() };
  const events = new Subject<AssistantSocketEvent>();
  const closed = new Subject<CloseEvent>();
  const socket = {
    connectedStatus: true,
    send: vi.fn(),
    receive: vi.fn(() => events),
    closed: () => closed
  };
  const ticket = {
    ticket: { init: initialized, principal: initialized ? 'alice' : '' },
    ticket$: new Subject<{ init: boolean; principal: string }>(),
    logout$: new BehaviorSubject(false),
    handleAuthFailure: vi.fn()
  };
  const host = new AssistantHostComponent(
    slots,
    { markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    message as unknown as NzMessageService,
    { getRestApiBase: () => '/zeppelin/api' } as BaseUrlService,
    socket as unknown as MessageService,
    ticket as unknown as TicketService
  );
  host.enabled = true;
  host.noteId = 'note-a';
  host.panelWidth = 370;
  host.ngOnChanges({ enabled: new SimpleChange(undefined, true, true) });
  host.ngOnInit();
  return { host, slots, message, socket, events, closed, ticket };
};

it('updates remote slots and width, then retires the panel when disabled', async () => {
  const { host, slots } = createHost();

  const navigation = document.createElement('span');
  slots.set({ element: navigation, kind: 'navigation' });
  await Promise.resolve();
  expect(host.assistantProps?.slots).toEqual([{ element: navigation, kind: 'navigation' }]);

  host.panelWidth = 400;
  host.ngOnChanges({ panelWidth: new SimpleChange(370, 400, false) });
  expect(host.assistantProps?.panelWidth).toBe(400);
  host.assistantProps?.onPanelVisibilityChange(true);
  expect(slots.panelOpen.value).toBe(true);

  const close = vi.spyOn(slots.panelCloseRequests, 'next');
  host.noteId = 'note-b';
  host.ngOnChanges({ noteId: new SimpleChange('note-a', 'note-b', false) });
  expect(close).toHaveBeenCalledOnce();

  host.enabled = false;
  host.ngOnChanges({ enabled: new SimpleChange(true, false, false) });
  expect(slots.panelOpen.value).toBe(false);
  expect(host.assistantProps).toBeNull();
  host.ngOnDestroy();
});

it('closes the panel when the notebook is destroyed', () => {
  const { host, slots } = createHost();
  host.assistantProps?.onPanelVisibilityChange(true);

  host.ngOnDestroy();
  expect(slots.panelOpen.value).toBe(false);
});

it('logs a remote failure, tells the user, and remounts on the next note', () => {
  const { host, message } = createHost();
  const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
  const error = new Error('remote unavailable');

  host.assistantProps?.onError?.(error);
  expect(consoleError).toHaveBeenCalledWith('React assistant error', error);
  expect(message.error).toHaveBeenCalledWith('Unable to load the AI Assistant.');
  expect(host.failed).toBe(true);

  host.noteId = 'note-b';
  host.ngOnChanges({ noteId: new SimpleChange('note-a', 'note-b', false) });
  expect(host.failed).toBe(false);
  consoleError.mockRestore();
  host.ngOnDestroy();
});

it('passes notebook connection and navigation ports without exposing credentials', () => {
  const { host, ticket } = createHost();
  const connection = host.assistantProps!.connection;
  expect(connection.apiBase).toBe('/zeppelin/api');
  expect(connection).not.toHaveProperty('ticket');
  expect(connection).not.toHaveProperty('principal');
  connection.onAuthError(401, '/login/redirect');
  expect(ticket.handleAuthFailure).toHaveBeenCalledWith(401, '/login/redirect');
  host.paragraphs = [{ id: 'paragraph-a', title: 'Current paragraph' }];
  host.ngOnChanges({ paragraphs: new SimpleChange([], host.paragraphs, false) });
  expect(host.assistantProps!.paragraphs).toBe(host.paragraphs);
  const open = vi.fn();
  host.openParagraph.subscribe(open);
  host.assistantProps!.onOpenParagraph('paragraph-a');
  expect(open).toHaveBeenCalledWith('paragraph-a');
  host.ngOnDestroy();
});

it('adapts SDK assistant operations, refuses disconnected sends and releases listeners', () => {
  const { host, socket, events, closed } = createHost();
  const port = host.assistantProps!.connection.socket;
  const eventListener = vi.fn();
  const closeListener = vi.fn();
  const unsubscribe = port.subscribe(eventListener);
  const unsubscribeClose = port.subscribeClose(closeListener);
  const message = { noteId: 'note-a', conversationId: 'conversation-a', content: 'hello' };
  port.send(message);
  expect(socket.send).toHaveBeenCalledWith(OP.ASSISTANT_SEND_MESSAGE, message);
  expect(socket.receive).toHaveBeenCalledWith(OP.ASSISTANT_EVENT);
  const event: AssistantSocketEvent = { conversationId: 'conversation-a', type: 'run.started', payload: {} };
  events.next(event);
  expect(eventListener).toHaveBeenCalledWith(event);
  closed.next(new CloseEvent('close'));
  expect(closeListener).toHaveBeenCalledOnce();
  socket.connectedStatus = false;
  expect(() => port.send(message)).toThrow('not connected');
  expect(socket.send).toHaveBeenCalledOnce();
  unsubscribe();
  unsubscribeClose();
  expect(events.observed).toBe(false);
  expect(closed.observed).toBe(false);
  host.ngOnDestroy();
});

it('keeps connection identity across layout updates and ordinary ticket refreshes', async () => {
  const { host, slots, ticket } = createHost();
  const connection = host.assistantProps!.connection;
  host.panelWidth = 400;
  host.ngOnChanges({ panelWidth: new SimpleChange(370, 400, false) });
  ticket.ticket$.next({ init: true, principal: 'alice' });
  slots.set({ element: document.createElement('div'), kind: 'panel' });
  await Promise.resolve();
  expect(host.assistantProps!.connection).toBe(connection);
  host.ngOnDestroy();
});

it('retires auth callbacks and socket events immediately on note transitions', async () => {
  const { host, ticket, events, closed } = createHost();
  const connection = host.assistantProps!.connection;
  const listener = vi.fn();
  const closeListener = vi.fn();
  connection.socket.subscribe(listener);
  connection.socket.subscribeClose(closeListener);
  host.retire();
  connection.onAuthError(405, null);
  events.next({ conversationId: 'old', type: 'run.completed', payload: {} });
  closed.next(new CloseEvent('close'));
  await Promise.resolve();
  expect(ticket.handleAuthFailure).not.toHaveBeenCalled();
  expect(listener).not.toHaveBeenCalled();
  expect(closeListener).not.toHaveBeenCalled();
  expect(host.assistantProps).toBeNull();
  expect(() => connection.socket.send({ noteId: 'note-a', conversationId: 'old', content: 'stale' })).toThrow();
  host.noteId = 'note-b';
  host.ngOnChanges({ noteId: new SimpleChange('note-a', 'note-b', false) });
  expect(host.assistantProps!.connection).not.toBe(connection);
  expect(host.assistantProps!.connection.sessionKey).toBe(connection.sessionKey);
  connection.onAuthError(405, null);
  expect(ticket.handleAuthFailure).not.toHaveBeenCalled();
  host.assistantProps!.connection.onAuthError(405, null);
  expect(ticket.handleAuthFailure).toHaveBeenCalledWith(405, null);
  host.ngOnDestroy();
  expect(events.observed).toBe(false);
  expect(closed.observed).toBe(false);
});

it('creates new session identities for logout and same-user relogin, not initial logout state', () => {
  const { host, ticket } = createHost();
  const initial = host.assistantProps!.connection;
  ticket.logout$.next(true);
  expect(host.assistantProps).toBeNull();
  initial.onAuthError(405, null);
  expect(ticket.handleAuthFailure).not.toHaveBeenCalled();
  ticket.ticket = { init: false, principal: '' };
  ticket.logout$.next(false);
  expect(host.assistantProps).toBeNull();
  ticket.ticket = { init: true, principal: 'alice' };
  ticket.ticket$.next(ticket.ticket);
  const loggedIn = host.assistantProps!.connection;
  expect(loggedIn.sessionKey).not.toBe(initial.sessionKey);
  expect(loggedIn).not.toBe(initial);
  initial.onAuthError(401, '/stale-login');
  expect(ticket.handleAuthFailure).not.toHaveBeenCalled();
  host.ngOnDestroy();
  loggedIn.onAuthError(405, null);
  expect(ticket.handleAuthFailure).not.toHaveBeenCalled();
});

it('resets the session on principal initialization or user change', () => {
  const { host, ticket } = createHost();
  const initial = host.assistantProps!.connection;
  ticket.ticket$.next({ init: false, principal: '' });
  expect(host.assistantProps).toBeNull();
  ticket.ticket$.next({ init: true, principal: 'bob' });
  expect(host.assistantProps!.connection.sessionKey).not.toBe(initial.sessionKey);
  initial.onAuthError(405, null);
  expect(ticket.handleAuthFailure).not.toHaveBeenCalled();
  host.ngOnDestroy();
});

it('waits for the initial ticket and does not reset on a duplicate initialized ticket', () => {
  const { host, ticket } = createHost(false);
  expect(host.assistantProps).toBeNull();
  ticket.ticket = { init: true, principal: 'alice' };
  ticket.ticket$.next(ticket.ticket);
  const connection = host.assistantProps!.connection;
  ticket.ticket$.next({ ...ticket.ticket });
  expect(host.assistantProps!.connection).toBe(connection);
  host.ngOnDestroy();
});

it('ignores authentication failures after the feature is disabled', () => {
  const { host, ticket } = createHost();
  const connection = host.assistantProps!.connection;
  host.enabled = false;
  host.ngOnChanges({ enabled: new SimpleChange(true, false, false) });
  connection.onAuthError(405, null);
  expect(ticket.handleAuthFailure).not.toHaveBeenCalled();
  host.ngOnDestroy();
});

it('shares one real logout with an overlapping Angular 405 and completes shared ticket cleanup', async () => {
  const requests: string[] = [];
  const logoutResponse = new Subject<HttpEvent<unknown>>();
  const failure = new HttpErrorResponse({ status: 405, url: '/zeppelin/api/login' });
  let interceptor: AppHttpInterceptor;
  const backend: HttpHandler = {
    handle: request => {
      requests.push(request.url);
      return request.url.endsWith('/login/logout') ? logoutResponse : throwError(() => failure);
    }
  };
  const client = new HttpClient({ handle: request => interceptor.intercept(request, backend) });
  const baseUrl = { getRestApiBase: () => '/zeppelin/api' } as BaseUrlService;
  const navigate = vi.fn(() => Promise.resolve(true));
  const ticket = new TicketService(
    client,
    baseUrl,
    { navigate } as unknown as Router,
    { success: vi.fn() } as unknown as NzMessageService
  );
  ticket.ticket.init = true;
  ticket.ticket.principal = 'alice';
  interceptor = new AppHttpInterceptor(ticket);
  const host = new AssistantHostComponent(
    new AssistantSlots(),
    { markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    { error: vi.fn() } as unknown as NzMessageService,
    baseUrl,
    {} as MessageService,
    ticket
  );
  host.enabled = true;
  host.noteId = 'note-a';
  host.panelWidth = 370;
  host.ngOnChanges({ enabled: new SimpleChange(undefined, true, true) });
  host.ngOnInit();
  const connection = host.assistantProps!.connection;

  connection.onAuthError(405, null);
  expect(host.assistantProps).toBeNull();
  expect(ticket.logout$.value).toBe(true);
  await expect(firstValueFrom(client.get('/zeppelin/api/notebook'))).rejects.toBe(failure);
  expect(requests).toEqual(['/zeppelin/api/login/logout', '/zeppelin/api/notebook']);
  connection.onAuthError(405, null);
  expect(requests).toHaveLength(2);

  logoutResponse.error(failure);
  expect(requests).toHaveLength(2);
  expect(ticket.ticket.init).toBe(false);
  expect(ticket.ticket.principal).toBe('');
  expect(ticket.logout$.value).toBe(false);
  expect(navigate).toHaveBeenCalledExactlyOnceWith(['/login']);
  host.ngOnDestroy();
});
