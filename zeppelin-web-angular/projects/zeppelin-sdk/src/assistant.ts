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

import type { AssistantParagraphRef, AssistantRevealResult, AssistantSlot } from './assistant-ui';

import type {
  AssistantEvent as AssistantSocketEvent,
  AssistantSendMessage
} from './interfaces/message-assistant.interface';

/** ASSISTANT_EVENT as the server sends it; the remote maps it to its run events. */
export type { AssistantSocketEvent };

/** The host's notebook WebSocket, narrowed to the assistant ops. */
export interface AssistantSocket {
  /** Stable host connection identity across notebook adapters. */
  readonly connectionKey?: object;
  /** Aborted when the host retires this notebook/account adapter, without closing the shared connection. */
  readonly signal?: AbortSignal;
  send(message: AssistantSendMessage): void;
  subscribe(listener: (event: AssistantSocketEvent) => void): () => void;
  /** The connection closed. The server sends a run's events only to the connection that started it. */
  subscribeClose(listener: () => void): () => void;
}

/** Props the Angular notebook passes to the `./AssistantWorkspace` remote. A type alias, so it fits `ReactProps`. */
export type AssistantHostProps = {
  noteId: string;
  /** The host's REST base (`.../api`); the remote calls the conversation REST API itself. */
  apiBase: string;
  /** The host's notebook WebSocket, used to send messages and receive run events. */
  socket: AssistantSocket;
  draftOwner?: string;
  slots: AssistantSlot[];
  onAuthError?: (status: number, location: string | null) => void;
  onError?: (error: unknown) => void;
  /** Keeps the host sidebar to one open view at a time. */
  onPanelVisibilityChange?: (visible: boolean) => void;
  subscribePanelClose?: (listener: () => void) => () => void;
  /** Host-side scroll and highlight for a paragraph an answer links to; notebook DOM stays with Angular. */
  revealParagraph?: (paragraphId: string) => Promise<AssistantRevealResult>;
  /** The notebook's paragraphs in order, as data so labelling them never calls into the host. */
  paragraphs?: AssistantParagraphRef[];
  /** The notebook sidebar's width, which the panel shares so switching views keeps it. */
  panelWidth?: number;
  /** A resize of the panel, at the end of a drag or per arrow key. */
  onPanelWidthChange?: (width: number) => void;
};
