import { OnDestroy } from '@angular/core';
import { Subscriber } from 'rxjs';

import { MessageReceiveDataTypeMap, ReceiveArgumentsType } from '@zeppelin/sdk';
import { MessageService } from '@zeppelin/services';

export class MessageListenersManager implements OnDestroy {
  __zeppelinMessageListeners__: Array<() => void>;
  __zeppelinMessageListeners$__ = new Subscriber();
  constructor(public messageService: MessageService) {
    if (this.__zeppelinMessageListeners__) {
      this.__zeppelinMessageListeners__.forEach(fn => fn.apply(this));
    }
  }

  ngOnDestroy(): void {
    this.__zeppelinMessageListeners$__.unsubscribe();
    this.__zeppelinMessageListeners$__ = null;
  }
}

export function MessageListener<K extends keyof MessageReceiveDataTypeMap>(op: K) {
  return function(
    target: MessageListenersManager,
    propertyKey: string,
    descriptor: TypedPropertyDescriptor<ReceiveArgumentsType<K>>
  ) {
    const oldValue = descriptor.value as ReceiveArgumentsType<K>;

    const fn = function() {
      // tslint:disable:no-invalid-this
      this.__zeppelinMessageListeners$__.add(
        this.messageService.receive(op).subscribe(data => {
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
