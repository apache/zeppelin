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

import { ParagraphState } from './interfaces/message-paragraph.interface';

// Job.Status.isCompleted: FINISHED, ERROR and ABORT terminate a run.
const terminalStates: Record<ParagraphState, boolean> = {
  UNKNOWN: false,
  READY: false,
  PENDING: false,
  RUNNING: false,
  FINISHED: true,
  ERROR: true,
  ABORT: true
};

export const isTerminalParagraphState = (status: ParagraphState | undefined): boolean =>
  status !== undefined && terminalStates[status];
