/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export const ParagraphStatus = {
  READY: 'READY',
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
  FINISHED: 'FINISHED',
  ABORT: 'ABORT',
  ERROR: 'ERROR',
}

export function isParagraphRunning (paragraph) {
  if (!paragraph) { return false }
  const status = paragraph.status
  if (!status) { return false }

  return status === ParagraphStatus.PENDING || status === ParagraphStatus.RUNNING
}
