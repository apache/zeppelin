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

import { ParagraphActions } from './paragraph-actions';

export type NullableKeyboardEvent = KeyboardEvent | null;

interface NotebookParagraphKeyboardEventHandler {
  handleRun(): void;
  handleRunAbove(): void;
  handleRunBelow(): void;
  handleCancel(): void;
  handleMoveCursorUp(): void;
  handleMoveCursorDown(): void;
  handleDelete(): void;
  handleInsertAbove(): void;
  handleInsertBelow(): void;
  handleInsertCopyOfParagraphBelow(): void;
  handleMoveParagraphUp(): void;
  handleMoveParagraphDown(): void;
  handleSwitchEditor(): void;
  handleSwitchEnable(): void;
  handleSwitchOutputShow(): void;
  handleSwitchLineNumber(): void;
  handleSwitchTitleShow(): void;
  handleClear(): void;
  handleLink(): void;
  handleReduceWidth(): void;
  handleIncreaseWidth(): void;
  handleCutLine(): void;
  handlePasteLine(): void;
  handleSearchInsideCode(): void;
  handleFindInCode(): void;
}

// If any ParagraphActions is missing here, TS compiler will complain.
export const ParagraphActionToHandlerName = {
  [ParagraphActions.Run]: 'handleRun',
  [ParagraphActions.RunAbove]: 'handleRunAbove',
  [ParagraphActions.RunBelow]: 'handleRunBelow',
  [ParagraphActions.Cancel]: 'handleCancel',
  [ParagraphActions.MoveCursorUp]: 'handleMoveCursorUp',
  [ParagraphActions.MoveCursorDown]: 'handleMoveCursorDown',
  [ParagraphActions.Delete]: 'handleDelete',
  [ParagraphActions.InsertAbove]: 'handleInsertAbove',
  [ParagraphActions.InsertBelow]: 'handleInsertBelow',
  [ParagraphActions.InsertCopyOfParagraphBelow]: 'handleInsertCopyOfParagraphBelow',
  [ParagraphActions.MoveParagraphUp]: 'handleMoveParagraphUp',
  [ParagraphActions.MoveParagraphDown]: 'handleMoveParagraphDown',
  [ParagraphActions.SwitchEditor]: 'handleSwitchEditor',
  [ParagraphActions.SwitchEnable]: 'handleSwitchEnable',
  [ParagraphActions.SwitchOutputShow]: 'handleSwitchOutputShow',
  [ParagraphActions.SwitchLineNumber]: 'handleSwitchLineNumber',
  [ParagraphActions.SwitchTitleShow]: 'handleSwitchTitleShow',
  [ParagraphActions.Clear]: 'handleClear',
  [ParagraphActions.Link]: 'handleLink',
  [ParagraphActions.ReduceWidth]: 'handleReduceWidth',
  [ParagraphActions.IncreaseWidth]: 'handleIncreaseWidth',
  [ParagraphActions.CutLine]: 'handleCutLine',
  [ParagraphActions.PasteLine]: 'handlePasteLine',
  [ParagraphActions.SearchInsideCode]: 'handleSearchInsideCode',
  [ParagraphActions.FindInCode]: 'handleFindInCode'
} as const;
// TODO: Replace `as const` with `satisfies Record<ParagraphActions, keyof NotebookParagraphKeyboardEventHandler>` when typescript version is over 4.9.
//       This allows checking both keys and values at the type level,
//       while preserving the binding between them.

const MonacoHandledParagraphActions = [
  ParagraphActions.MoveCursorUp,
  ParagraphActions.MoveCursorDown,
  ParagraphActions.SwitchEditor,
  ParagraphActions.CutLine,
  ParagraphActions.PasteLine,
  ParagraphActions.SearchInsideCode
] as const;
// TODO: Replace `as const` with `satisfies ParagraphActions[]` when typescript version is over 4.9.
//       This ensures that the array contains only valid ParagraphActions,
//       while preserving the literal value of the each element.

type MonacoHandledParagraphAction = typeof MonacoHandledParagraphActions[number];

type MonacoHandledParagraphActionHandlerName = typeof ParagraphActionToHandlerName[MonacoHandledParagraphAction];

export type MonacoKeyboardEventHandler = Pick<
  NotebookParagraphKeyboardEventHandler,
  MonacoHandledParagraphActionHandlerName
>;

export type AngularKeyboardEventHandler = Omit<
  NotebookParagraphKeyboardEventHandler,
  MonacoHandledParagraphActionHandlerName
>;
