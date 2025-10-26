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
import { Subscriber } from 'rxjs';

import { Message, MessageReceiveDataTypeMap, ReceiveArgumentsType } from '@zeppelin/sdk';

@Component({ template: '' })
// eslint-disable-next-line @angular-eslint/component-class-suffix
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

export function MessageListener<K extends keyof MessageReceiveDataTypeMap>(op: K) {
  return function (
    target: MessageListenersManager,
    propertyKey: string,
    descriptor: TypedPropertyDescriptor<ReceiveArgumentsType<K>>
  ) {
    const oldValue = descriptor.value as ReceiveArgumentsType<K>;

    // eslint-disable-next-line no-invalid-this
    const fn = function (this: MessageListenersManager) {
      // eslint-disable-next-line no-invalid-this
      if (!this.__zeppelinMessageListeners$__) {
        throw new Error('__zeppelinMessageListeners$__ is not defined');
      }
      // eslint-disable-next-line no-invalid-this
      this.__zeppelinMessageListeners$__.add(
        // eslint-disable-next-line no-invalid-this
        this.messageService.receive(op).subscribe(data => {
          // @ts-ignore
          // eslint-disable-next-line no-invalid-this
          oldValue.apply(this, [data]);
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
}
