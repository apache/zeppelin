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

import { Subject } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Message, OP, MessageReceiveDataTypeMap, type WebSocketMessage } from '@zeppelin/sdk';
import { MessageEnvelopeListener, MessageListener, MessageListenersManager } from './message-listener';

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe('MessageListener', () => {
  it('logs handler errors with the OP, rethrows them, and keeps the subscription active', () => {
    // RxJS rethrows an error thrown inside `next` from a timer, so the subscription itself survives.
    vi.useFakeTimers();

    const received$ = new Subject<MessageReceiveDataTypeMap[OP.NOTE]>();
    const messageService = {
      receive: vi.fn(() => received$.asObservable())
    } as unknown as Message;

    const error = new Error('boom');
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

    class TestComponent extends MessageListenersManager {
      calls = 0;

      handleNote(_data: MessageReceiveDataTypeMap[OP.NOTE]): void {
        this.calls++;

        if (this.calls === 1) {
          throw error;
        }
      }
    }

    const descriptor = Object.getOwnPropertyDescriptor(TestComponent.prototype, 'handleNote')!;

    MessageListener(OP.NOTE)(TestComponent.prototype, 'handleNote', descriptor);

    const component = new TestComponent(messageService);
    const data = {} as MessageReceiveDataTypeMap[OP.NOTE];

    received$.next(data);
    received$.next(data);

    expect(component.calls).toBe(2);
    expect(consoleError).toHaveBeenCalledWith(`Failed to handle WebSocket OP ${String(OP.NOTE)}`, error);
    expect(() => vi.runAllTimers()).toThrow(error);
  });

  it('passes received data to the handler', () => {
    const received$ = new Subject<MessageReceiveDataTypeMap[OP.NOTE]>();
    const messageService = {
      receive: vi.fn(() => received$.asObservable())
    } as unknown as Message;

    class TestComponent extends MessageListenersManager {
      receivedData?: MessageReceiveDataTypeMap[OP.NOTE];

      handleNote(data: MessageReceiveDataTypeMap[OP.NOTE]): void {
        this.receivedData = data;
      }
    }

    const descriptor = Object.getOwnPropertyDescriptor(TestComponent.prototype, 'handleNote')!;

    MessageListener(OP.NOTE)(TestComponent.prototype, 'handleNote', descriptor);

    const component = new TestComponent(messageService);
    const data = {} as MessageReceiveDataTypeMap[OP.NOTE];

    received$.next(data);

    expect(component.receivedData).toBe(data);
  });
});

describe('MessageEnvelopeListener', () => {
  it('passes the received message envelope with msgId to the handler', () => {
    const received$ = new Subject<WebSocketMessage<MessageReceiveDataTypeMap, OP.NOTE>>();
    const messageService = {
      receiveEnvelope: vi.fn(() => received$.asObservable())
    } as unknown as Message;

    class TestComponent extends MessageListenersManager {
      receivedMessage?: WebSocketMessage<MessageReceiveDataTypeMap, OP.NOTE>;

      handleNote(message: WebSocketMessage<MessageReceiveDataTypeMap, OP.NOTE>): void {
        this.receivedMessage = message;
      }
    }

    const descriptor = Object.getOwnPropertyDescriptor(TestComponent.prototype, 'handleNote')!;

    MessageEnvelopeListener(OP.NOTE)(TestComponent.prototype, 'handleNote', descriptor);

    const component = new TestComponent(messageService);
    const envelope: WebSocketMessage<MessageReceiveDataTypeMap, OP.NOTE> = {
      op: OP.NOTE,
      msgId: 'note-request-1',
      data: {} as MessageReceiveDataTypeMap[OP.NOTE]
    };

    received$.next(envelope);

    expect(component.receivedMessage).toBe(envelope);
    expect(messageService.receiveEnvelope).toHaveBeenCalledWith(OP.NOTE);
  });
});
