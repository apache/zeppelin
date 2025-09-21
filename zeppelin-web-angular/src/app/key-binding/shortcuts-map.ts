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

// On macOS, pressing Option(Alt) + a letter produces a non-ASCII character
// Shortcuts must use this resulting character instead of the plain letter for macOS
import { ParagraphActions } from './paragraph-actions';

export const ShortcutsMap: Record<ParagraphActions, string | string[]> = {
  [ParagraphActions.Run]: 'shift.enter', // Run paragraph
  [ParagraphActions.RunAbove]: 'control.shift.arrowup', // Run all above paragraphs (exclusive)
  [ParagraphActions.RunBelow]: 'control.shift.arrowdown', // Run all below paragraphs (inclusive)
  [ParagraphActions.Cancel]: ['control.alt.c', 'control.alt.ç'], // Cancel
  [ParagraphActions.MoveCursorUp]: 'control.p', // Move cursor Up
  [ParagraphActions.MoveCursorDown]: 'control.n', // Move cursor Down
  [ParagraphActions.Delete]: ['control.alt.d', 'control.alt.∂'], // Remove paragraph
  [ParagraphActions.InsertAbove]: ['control.alt.a', 'control.alt.å'], // Insert new paragraph above
  [ParagraphActions.InsertBelow]: ['control.alt.b', 'control.alt.∫'], // Insert new paragraph below
  [ParagraphActions.InsertCopyOfParagraphBelow]: 'control.shift.c', // Insert copy of paragraph below
  [ParagraphActions.MoveParagraphUp]: ['control.alt.k', 'control.alt.˚'], // Move paragraph Up
  [ParagraphActions.MoveParagraphDown]: ['control.alt.j', 'control.alt.∆'], // Move paragraph Down
  [ParagraphActions.SwitchEditor]: ['control.alt.e'], // Toggle editor
  [ParagraphActions.SwitchEnable]: ['control.alt.r', 'control.alt.®'], // Enable/Disable run paragraph
  [ParagraphActions.SwitchOutputShow]: ['control.alt.o', 'control.alt.ø'], // Toggle output
  [ParagraphActions.SwitchLineNumber]: ['control.alt.m', 'control.alt.µ'], // Toggle line number
  [ParagraphActions.SwitchTitleShow]: ['control.alt.t', 'control.alt.†'], // Toggle title
  [ParagraphActions.Clear]: ['control.alt.l', 'control.alt.¬'], // Clear output
  [ParagraphActions.Link]: ['control.alt.w', 'control.alt.∑'], // Link this paragraph
  [ParagraphActions.ReduceWidth]: ['control.shift.-', 'control.shift._'], // Reduce paragraph width
  [ParagraphActions.IncreaseWidth]: ['control.shift.=', 'control.shift.+'], // Increase paragraph width
  // Auto-completion - No longer needed; always applied now
  [ParagraphActions.CutLine]: ['control.k'], // Cut the line
  [ParagraphActions.PasteLine]: ['control.y'], // Paste the line
  // Move cursor to the beginning - System shortcut
  // Move cursor at the end - System shortcut
  [ParagraphActions.SearchInsideCode]: ['control.s'],
  // TODO: Check after the search code is implemented in action-bar.component.ts
  [ParagraphActions.FindInCode]: ['control.alt.f', 'control.alt.ƒ'] // Find in code
};
