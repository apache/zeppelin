import { afterEach, describe, expect, it, vi } from 'vitest';

import type { MessageInterceptor } from '@zeppelin/interfaces';
import { MessageService } from './message.service';
import { BaseUrlService } from './base-url.service';
import { TicketService } from './ticket.service';

const createService = (): MessageService =>
  new MessageService(
    { getWebsocketUrl: vi.fn() } as unknown as BaseUrlService,
    { originTicket: {} } as unknown as TicketService,
    undefined as unknown as MessageInterceptor
  );

const getPendingNoteRequests = (service: MessageService): Map<string, string> =>
  (
    service as unknown as {
      pendingNoteRequests: Map<string, string>;
    }
  ).pendingNoteRequests;

afterEach(() => {
  vi.restoreAllMocks();
});

describe('MessageService', () => {
  it('rejects a missing pending request id', () => {
    const service = createService();

    expect(service.consumePendingNoteRequest(undefined, 'note-a')).toBe(false);
  });

  it('rejects an unknown pending request id', () => {
    const service = createService();

    expect(service.consumePendingNoteRequest('unknown-msg-id', 'note-a')).toBe(false);
  });

  it('accepts a pending request for the active note', () => {
    const service = createService();

    const pendingNoteRequests = getPendingNoteRequests(service);

    pendingNoteRequests.set('msg-1', 'note-a');

    expect(service.consumePendingNoteRequest('msg-1', 'note-a')).toBe(true);
  });

  it('rejects a pending request for a different active note', () => {
    const service = createService();

    const pendingNoteRequests = getPendingNoteRequests(service);

    pendingNoteRequests.set('msg-1', 'note-a');

    expect(service.consumePendingNoteRequest('msg-1', 'note-b')).toBe(false);
    expect(service.consumePendingNoteRequest('msg-1', 'note-a')).toBe(false);
  });

  it('distinguishes two outstanding requests by message id', () => {
    const service = createService();

    const pendingNoteRequests = getPendingNoteRequests(service);

    pendingNoteRequests.set('msg-1', 'note-a');
    pendingNoteRequests.set('msg-2', 'note-b');

    expect(service.consumePendingNoteRequest('msg-1', 'note-a')).toBe(true);
    expect(service.consumePendingNoteRequest('msg-2', 'note-b')).toBe(true);
  });

  it('records the note context when sending an interpreter bindings request', () => {
    const service = createService();

    const ws = {
      next: vi.fn()
    };

    (
      service as unknown as {
        ws: { next: (message: unknown) => void };
      }
    ).ws = ws;

    service.getInterpreterBindings('note-a');

    const sentMessage = ws.next.mock.calls[0][0] as {
      msgId?: string;
    };

    expect(sentMessage.msgId).toBeDefined();
    expect(service.consumePendingNoteRequest(sentMessage.msgId, 'note-a')).toBe(true);
  });
});
