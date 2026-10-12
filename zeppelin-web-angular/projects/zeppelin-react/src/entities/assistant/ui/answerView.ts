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

import type { ReactNode } from 'react';
import type { AssistantMessage } from '../model/assistantContract';

/** A tool the assistant called during a live run. `tool_call.done` may omit the name, so it is optional. */
export interface AssistantToolActivity {
  id: string;
  name?: string;
  status: 'running' | 'done';
}

/**
 * Why an answer stopped: the server reported `run.failed`, the stream ended early (`AssistantStreamError`),
 * or the connection dropped and only a reload shows where the answer got to (`AssistantConnectionError`).
 */
export type AssistantAnswerErrorKind = 'failed' | 'stream' | 'connection';

/** What one answer shows. The panel derives it from run events; the components only display it. */
export type AssistantAnswerView =
  | { status: 'loading' }
  | { status: 'streaming'; content: string; tools?: AssistantToolActivity[] }
  | { status: 'done'; message: AssistantMessage }
  | {
      status: 'error';
      kind: AssistantAnswerErrorKind;
      message: string;
      /** The part of the answer that arrived before it stopped. */
      content?: string;
      tools?: AssistantToolActivity[];
    };

/** Renders an answer body. Plain text until the Markdown answer component is wired in. */
export type AssistantContentRenderer = (content: string) => ReactNode;
