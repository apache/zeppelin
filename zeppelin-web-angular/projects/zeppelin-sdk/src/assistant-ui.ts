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

/** The notebook sidebar's width: its default and its resize range. The assistant panel shares it. */
export const NOTEBOOK_SIDEBAR_WIDTH = { initial: 370, min: 280, max: 800 } as const;

/** Result of a host reveal: scrolled to it, already visible, or not rendered. */
export type AssistantRevealResult = 'shown' | 'visible' | 'missing';

/** A notebook paragraph as the remote sees it: enough to label and link it. */
export interface AssistantParagraphRef {
  id: string;
  title?: string;
}
