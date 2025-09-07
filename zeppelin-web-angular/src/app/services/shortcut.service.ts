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

import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { EventManager } from '@angular/platform-browser';
import { Observable } from 'rxjs';

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

// On macOS, pressing Option(Alt) + a letter produces a non-ASCII character
// Shortcuts must use this resulting character instead of the plain letter for macOS
export const ShortcutsMap = {
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
  [ParagraphActions.SwitchEnable]: ['control.alt.r', 'control.alt.®'], // Enable/Disable run paragraph
  [ParagraphActions.SwitchOutputShow]: ['control.alt.o', 'control.alt.ø'], // Toggle output
  // Toggle editor - Shortcut logic is implemented in the editor component
  [ParagraphActions.SwitchLineNumber]: ['control.alt.m', 'control.alt.µ'], // Toggle line number
  [ParagraphActions.SwitchTitleShow]: ['control.alt.t', 'control.alt.†'], // Toggle title
  [ParagraphActions.Clear]: ['control.alt.l', 'control.alt.¬'], // Clear output
  [ParagraphActions.Link]: ['control.alt.w', 'control.alt.∑'], // Link this paragraph
  [ParagraphActions.ReduceWidth]: 'control.shift._', // Reduce paragraph width
  [ParagraphActions.IncreaseWidth]: 'control.shift.=', // Increase paragraph width
  // Auto-completion - No longer needed; always applied now
  // Cut the line - Shortcut logic is implemented in the editor component
  // Paste the line - Shortcut logic is implemented in the editor component
  // Search inside the code - Shortcut logic is implemented in the editor component
  // Move cursor to the beginning - System shortcut
  // Move cursor at the end - System shortcut
  // TODO: Check after the search code is implemented in action-bar.component.ts
  [ParagraphActions.FindInCode]: ['control.alt.f', 'control.alt.ƒ'] // Find in code
};

export interface ShortcutEvent {
  event: KeyboardEvent;
  keybindings: string;
}

export interface ShortcutOption {
  scope?: HTMLElement;
  keybindings: string;
}

@Injectable({
  providedIn: 'root'
})
export class ShortcutService {
  private element: HTMLElement;

  // tslint:disable-next-line:no-any
  constructor(private eventManager: EventManager, @Inject(DOCUMENT) _document: any) {
    this.element = _document;
  }

  forkByElement(element: HTMLElement) {
    return new ShortcutService(this.eventManager, element);
  }

  bindShortcut(option: ShortcutOption): Observable<ShortcutEvent> {
    const host = option.scope || this.element;
    const eventName = `keydown.${option.keybindings}`;
    // tslint:disable-next-line:ban-types
    let dispose: Function;
    return new Observable<ShortcutEvent>(observer => {
      const handler = (event: KeyboardEvent) => {
        observer.next({
          event,
          keybindings: option.keybindings
        });
      };

      dispose = this.eventManager.addEventListener(host, eventName, handler);

      return () => {
        dispose();
      };
    });
  }
}
