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

export interface NotebookParagraphKeyboardEventHandler {
  handleRun(event: KeyboardEvent): void;
  handleRunAbove(event: KeyboardEvent): void;
  handleRunBelow(event: KeyboardEvent): void;
  handleCancel(event: KeyboardEvent): void;
  handleMoveCursorUp(event: KeyboardEvent): void;
  handleMoveCursorDown(event: KeyboardEvent): void;
  handleDelete(event: KeyboardEvent): void;
  handleInsertAbove(event: KeyboardEvent): void;
  handleInsertBelow(event: KeyboardEvent): void;
  handleInsertCopyOfParagraphBelow(event: KeyboardEvent): void;
  handleMoveParagraphUp(event: KeyboardEvent): void;
  handleMoveParagraphDown(event: KeyboardEvent): void;
  handleSwitchEnable(event: KeyboardEvent): void;
  handleSwitchOutputShow(event: KeyboardEvent): void;
  handleSwitchLineNumber(event: KeyboardEvent): void;
  handleSwitchTitleShow(event: KeyboardEvent): void;
  handleClear(event: KeyboardEvent): void;
  handleLink(event: KeyboardEvent): void;
  handleReduceWidth(event: KeyboardEvent): void;
  handleIncreaseWidth(event: KeyboardEvent): void;
  handleFindInCode(event: KeyboardEvent): void;
}
