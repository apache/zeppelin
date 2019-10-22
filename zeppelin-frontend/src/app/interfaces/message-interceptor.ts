import { InjectionToken } from '@angular/core';
import { MessageReceiveDataTypeMap, WebSocketMessage } from '@zeppelin/sdk';

export interface MessageInterceptor {
  received(data: WebSocketMessage<keyof MessageReceiveDataTypeMap>): WebSocketMessage<keyof MessageReceiveDataTypeMap>;
}

export const MESSAGE_INTERCEPTOR = new InjectionToken<MessageInterceptor>('MESSAGE_INTERCEPTOR');
