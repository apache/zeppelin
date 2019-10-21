import { MixMessageDataTypeMap } from './message-data-type-map.interface';

export interface WebSocketMessage<K extends keyof MixMessageDataTypeMap> {
  op: K;
  data?: MixMessageDataTypeMap[K];
  ticket?: string; // default 'anonymous'
  principal?: string; // default 'anonymous'
  roles?: string; // default '[]'
}
