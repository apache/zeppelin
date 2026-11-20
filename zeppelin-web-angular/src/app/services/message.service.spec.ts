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

import { TestBed } from '@angular/core/testing';
import { NzMessageService } from 'ng-zorro-antd/message';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MessageInterceptor } from '@zeppelin/interfaces';
import { WorkspaceComponent } from '../pages/workspace/workspace.component';
import { BaseUrlService } from './base-url.service';
import { MessageService } from './message.service';
import { TicketService } from './ticket.service';

vi.mock('@zeppelin/visualizations', () => ({ setTheme: vi.fn() }));

const offlineServices: MessageService[] = [];
const offlineMessageService = (): MessageService => {
  const service = new MessageService({} as BaseUrlService, {} as TicketService, null as unknown as MessageInterceptor);
  offlineServices.push(service);
  service.setOnline(false);
  service.setTicket({ principal: 'viewer', ticket: 'ticket', roles: '[]' });
  service.setWsUrl('ws://localhost/ws');
  service.connect();
  return service;
};

describe('MessageService local add focus', () => {
  afterEach(() => {
    for (const service of offlineServices.splice(0)) {
      service.ngOnDestroy();
    }
  });

  it.each([
    ['insertParagraph', (service: MessageService) => service.insertParagraph(0)],
    ['copyParagraph', (service: MessageService) => service.copyParagraph(1, undefined, 'text', {}, {})]
  ])('records the msgId returned by %s once', (_method, add) => {
    const service = offlineMessageService();

    const msgId = add(service);

    expect(service.consumeLocalAddFocusMsgId(msgId)).toBe(true);
    expect(service.consumeLocalAddFocusMsgId(msgId)).toBe(false);
  });

  it('does not record a msgId for a paragraph added by another client', () => {
    const service = offlineMessageService();
    service.insertParagraph(0);

    expect(service.consumeLocalAddFocusMsgId('other-client-1')).toBe(false);
  });
});

class BootstrapWebSocket extends EventTarget {
  static instances: BootstrapWebSocket[] = [];
  readyState = 0;
  onopen: ((event: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  send = vi.fn();

  constructor(readonly url: string) {
    super();
    BootstrapWebSocket.instances.push(this);
  }

  open(): void {
    this.readyState = 1;
    const event = new Event('open');
    this.onopen?.(event);
    this.dispatchEvent(event);
  }

  close(): void {
    this.readyState = 3;
    const event = new CloseEvent('close', { code: 1000, wasClean: true });
    this.onclose?.(event);
    this.dispatchEvent(event);
  }
}

describe('MessageService browser connectivity at bootstrap', () => {
  let service: MessageService;
  const ticket = { principal: 'viewer', ticket: 'viewer-ticket', roles: '[]' };
  const url = 'ws://localhost/ws';

  beforeEach(() => {
    vi.useFakeTimers();
    BootstrapWebSocket.instances = [];
    vi.stubGlobal('WebSocket', BootstrapWebSocket);
    service = new MessageService(
      { getWebsocketUrl: () => url } as BaseUrlService,
      { originTicket: ticket } as TicketService,
      null as unknown as MessageInterceptor
    );
  });

  afterEach(() => {
    service.ngOnDestroy();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('does not attempt an offline bootstrap and resumes once with the original URL and ticket', () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    const connected = vi.fn();
    service.connectedStatus$.subscribe(connected);

    service.bootstrap();
    vi.advanceTimersByTime(60000);

    expect(BootstrapWebSocket.instances).toHaveLength(0);
    expect(service.connectedStatus).toBe(false);

    service.setOnline(true);
    service.setOnline(true);

    expect(BootstrapWebSocket.instances).toHaveLength(1);
    const socket = BootstrapWebSocket.instances[0];
    expect(socket.url).toBe(url);
    socket.open();
    expect(service.connectedStatus).toBe(true);
    expect(connected).toHaveBeenLastCalledWith(true);

    service.ping();
    expect(JSON.parse(socket.send.mock.calls[0][0] as string)).toMatchObject({ op: 'PING', ...ticket });
  });

  it('opens an online bootstrap immediately without duplicating an in-flight connection', () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true);

    service.bootstrap();
    service.bootstrap();

    expect(BootstrapWebSocket.instances).toHaveLength(1);
    BootstrapWebSocket.instances[0].open();
    expect(service.connectedStatus).toBe(true);
  });

  it('applies a new principal before resuming an offline connection at bootstrap', () => {
    const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    const auth = { originTicket: ticket };
    service = new MessageService(
      { getWebsocketUrl: () => url } as BaseUrlService,
      auth as TicketService,
      null as unknown as MessageInterceptor
    );
    service.bootstrap();
    service.listNodes();
    auth.originTicket = { ...ticket, principal: 'next-user', ticket: 'next-ticket' };
    online.mockReturnValue(true);
    service.bootstrap();

    expect(BootstrapWebSocket.instances).toHaveLength(1);
    const socket = BootstrapWebSocket.instances[0];
    socket.open();
    service.ping();
    expect(socket.send.mock.calls.map(([frame]) => JSON.parse(frame as string))).toEqual([
      expect.objectContaining({ op: 'PING', principal: 'next-user', ticket: 'next-ticket' })
    ]);
  });

  it('pauses and resumes physical connections through workspace browser events, including an initially offline page', () => {
    const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    TestBed.configureTestingModule({
      declarations: [WorkspaceComponent],
      providers: [
        { provide: MessageService, useValue: service },
        { provide: NzMessageService, useValue: { loading: () => ({ messageId: 'connecting' }), remove: vi.fn() } }
      ]
    }).overrideComponent(WorkspaceComponent, {
      set: { template: '', templateUrl: undefined, styles: [], styleUrls: [] }
    });
    const workspace = TestBed.createComponent(WorkspaceComponent);
    workspace.detectChanges();
    service.bootstrap();
    vi.advanceTimersByTime(60000);
    expect(BootstrapWebSocket.instances).toHaveLength(0);

    online.mockReturnValue(true);
    window.dispatchEvent(new Event('online'));
    window.dispatchEvent(new Event('online'));
    expect(BootstrapWebSocket.instances).toHaveLength(1);
    BootstrapWebSocket.instances[0].open();
    expect(service.connectedStatus).toBe(true);

    online.mockReturnValue(false);
    window.dispatchEvent(new Event('offline'));
    window.dispatchEvent(new Event('offline'));
    expect(BootstrapWebSocket.instances[0].readyState).toBe(3);
    expect(service.connectedStatus).toBe(false);
    vi.advanceTimersByTime(60000);
    expect(BootstrapWebSocket.instances).toHaveLength(1);

    online.mockReturnValue(true);
    window.dispatchEvent(new Event('online'));
    window.dispatchEvent(new Event('online'));
    expect(BootstrapWebSocket.instances).toHaveLength(2);
    BootstrapWebSocket.instances[1].open();
    expect(service.connectedStatus).toBe(true);
    expect(BootstrapWebSocket.instances.filter(socket => socket.readyState === 1)).toHaveLength(1);
    workspace.destroy();
  });
});
