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

import type { AssistantSocket } from './assistant';

/** The notebook sidebar's width: its default and its resize range. The assistant panel shares it. */
export const NOTEBOOK_SIDEBAR_WIDTH = { initial: 370, min: 280, max: 800 } as const;

/** A notebook paragraph as the remote sees it: enough to label and link it. */
export interface AssistantParagraphRef {
  id: string;
  title?: string;
}

/** Notebook DOM targets for the React navigation button and panel. */
export interface AssistantSlot {
  element: HTMLElement;
  kind: 'navigation' | 'panel';
}

/** Authentication session and notebook connection supplied by the Angular host. */
export interface AssistantConnection {
  apiBase: string;
  socket: AssistantSocket;
  /** Changes after logout or a change of authenticated user, not ordinary ticket refreshes. */
  sessionKey: number;
  onAuthError: (status: number, location: string | null) => void;
}

/** Props passed by the notebook host to the React Assistant. */
export type AssistantShellProps = {
  noteId: string;
  connection: AssistantConnection;
  paragraphs: AssistantParagraphRef[];
  onOpenParagraph: (paragraphId: string) => void;
  slots: AssistantSlot[];
  panelWidth: number;
  onPanelWidthChange: (width: number) => void;
  onPanelVisibilityChange: (visible: boolean) => void;
  subscribePanelClose: (listener: () => void) => () => void;
  onError?: (error: unknown) => void;
};
