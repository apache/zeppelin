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

import { describe, expect, it, vi } from 'vitest';

import { MessageInterceptor } from '@zeppelin/interfaces';
import { BaseUrlService } from './base-url.service';
import { MessageService } from './message.service';
import { TicketService } from './ticket.service';

const connectedMessageService = (): MessageService => {
  const service = new MessageService({} as BaseUrlService, {} as TicketService, null as unknown as MessageInterceptor);
  (service as unknown as { ws: { next: () => void } }).ws = { next: vi.fn() };
  return service;
};

describe('MessageService local add focus', () => {
  it.each([
    ['insertParagraph', (service: MessageService) => service.insertParagraph(0)],
    ['copyParagraph', (service: MessageService) => service.copyParagraph(1, undefined, 'text', {}, {})]
  ])('records the msgId returned by %s once', (_method, add) => {
    const service = connectedMessageService();

    const msgId = add(service);

    expect(service.consumeLocalAddFocusMsgId(msgId)).toBe(true);
    expect(service.consumeLocalAddFocusMsgId(msgId)).toBe(false);
  });

  it('does not record a msgId for a paragraph added by another client', () => {
    const service = connectedMessageService();
    service.insertParagraph(0);

    expect(service.consumeLocalAddFocusMsgId('other-client-1')).toBe(false);
  });
});
