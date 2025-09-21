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

export enum ParagraphActions {
  Run = 'Paragraph:Run',
  RunAbove = 'Paragraph:RunAbove',
  RunBelow = 'Paragraph:RunBelow',
  Cancel = 'Paragraph:Cancel',
  MoveCursorUp = 'Paragraph:MoveCursorUp',
  MoveCursorDown = 'Paragraph:MoveCursorDown',
  Delete = 'Paragraph:Delete',
  InsertAbove = 'Paragraph:InsertAbove',
  InsertBelow = 'Paragraph:InsertBelow',
  InsertCopyOfParagraphBelow = 'Paragraph:InsertCopyOfParagraphBelow',
  MoveParagraphUp = 'Paragraph:MoveParagraphUp',
  MoveParagraphDown = 'Paragraph:MoveParagraphDown',
  SwitchEnable = 'Paragraph:SwitchEnable',
  SwitchOutputShow = 'Paragraph:SwitchOutputShow',
  SwitchLineNumber = 'Paragraph:SwitchLineNumber',
  SwitchTitleShow = 'Paragraph:SwitchTitleShow',
  Clear = 'Paragraph:Clear',
  Link = 'Paragraph:Link',
  ReduceWidth = 'Paragraph:ReduceWidth',
  IncreaseWidth = 'Paragraph:IncreaseWidth',
  FindInCode = 'Paragraph:FindInCode'
}
