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

import { Component, OnDestroy } from '@angular/core';
import { Observable, Subscriber } from 'rxjs';

import { Message, MessageReceiveDataTypeMap } from '@zeppelin/sdk';

@Component({
  template: '',
  standalone: false
})
export class MessageListenersManager implements OnDestroy {
  __zeppelinMessageListeners__?: Array<() => void>;
  __zeppelinMessageListeners$__: Subscriber<unknown> | null = new Subscriber();
  constructor(public messageService: Message) {
    if (this.__zeppelinMessageListeners__) {
      this.__zeppelinMessageListeners__.forEach(fn => fn.apply(this));
    }
  }

  ngOnDestroy(): void {
    this.__zeppelinMessageListeners$__?.unsubscribe();
    this.__zeppelinMessageListeners$__ = null;
  }
}

type ListenerArgumentsType<T> = T extends undefined ? () => void : (data: T) => void;

const createMessageListener = <K extends keyof MessageReceiveDataTypeMap, T>(
  op: K,
  receiver: (messageService: Message, op: K) => Observable<T>
) => {
  return function (
    target: MessageListenersManager,
    propertyKey: string,
    descriptor: TypedPropertyDescriptor<ListenerArgumentsType<T>>
  ) {
    const oldValue = descriptor.value as ListenerArgumentsType<T>;

    const fn = function (this: MessageListenersManager) {
      if (!this.__zeppelinMessageListeners$__) {
        throw new Error('__zeppelinMessageListeners$__ is not defined');
      }

      this.__zeppelinMessageListeners$__.add(
        receiver(this.messageService, op).subscribe(data => {
          try {
            // @ts-ignore
            oldValue.apply(this, [data]);
          } catch (error) {
            console.error(`Failed to handle WebSocket OP ${String(op)}`, error);
            throw error;
          }
        })
      );
    };

    if (!target.__zeppelinMessageListeners__) {
      target.__zeppelinMessageListeners__ = [fn];
    } else {
      target.__zeppelinMessageListeners__.push(fn);
    }

    return descriptor;
  };
};

export const MessageListener = <K extends keyof MessageReceiveDataTypeMap>(op: K) => {
  return createMessageListener(op, (messageService, targetOp) => messageService.receive(targetOp));
};

export const MessageEnvelopeListener = <K extends keyof MessageReceiveDataTypeMap>(op: K) => {
  return createMessageListener(op, (messageService, targetOp) => messageService.receiveEnvelope(targetOp));
};
