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

export type EditorMode = 'ace/mode/scala' | 'ace/mode/python' | 'ace/mode/sql' | 'ace/mode/markdown' | 'ace/mode/sh';

export type EditorCompletionKey = 'TAB' | string;
export type EditorLanguage = 'scala' | 'python' | 'sql' | 'markdown' | 'sh' | string;

export interface Ticket {
  principal: string;
  ticket: string;
  redirectURL?: string;
  roles: string;
}

export interface ClientConfigurations {
  wsMaxMessageSize: number;
}

export interface ErrorInfo {
  info?: string;
}

export interface AuthInfo {
  info?: string;
}
