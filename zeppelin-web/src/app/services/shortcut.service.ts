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
  EditMode = 'Paragraph:EditMode',
  CommandMode = 'Paragraph:CommandMode',
  Run = 'Paragraph:Run',
  RunBelow = 'Paragraph:RunBelow',
  Cancel = 'Paragraph:Cancel',
  Clear = 'Paragraph:Clear',
  ReduceWidth = 'Paragraph:ReduceWidth',
  IncreaseWidth = 'Paragraph:IncreaseWidth',
  Delete = 'Paragraph:Delete',
  MoveToUp = 'Paragraph:MoveToUp',
  MoveToDown = 'Paragraph:MoveToDown',
  SelectAbove = 'Paragraph:SelectAbove',
  SelectBelow = 'Paragraph:SelectBelow',
  InsertAbove = 'Paragraph:InsertAbove',
  InsertBelow = 'Paragraph:InsertBelow',
  SwitchLineNumber = 'Paragraph:SwitchLineNumber',
  SwitchTitleShow = 'Paragraph:SwitchTitleShow',
  SwitchOutputShow = 'Paragraph:SwitchOutputShow',
  SwitchEditorShow = 'Paragraph:SwitchEditorShow',
  SwitchEnable = 'Paragraph:SwitchEnable',
  Link = 'Paragraph:Link'
}

export const ShortcutsMap = {
  [ParagraphActions.EditMode]: 'enter',
  [ParagraphActions.CommandMode]: 'esc',
  [ParagraphActions.Run]: 'shift.enter',
  [ParagraphActions.RunBelow]: 'shift.ctrlCmd.enter',
  [ParagraphActions.Cancel]: 'shift.ctrlCmd.c',
  // Need register special character `¬` in MacOS
  [ParagraphActions.Clear]: ['alt.ctrlCmd.l', 'alt.ctrlCmd.¬'],
  // Need register special character `†` in MacOS
  [ParagraphActions.Link]: ['alt.ctrlCmd.t', 'alt.ctrlCmd.†'],
  // Need register special character `®` in MacOS
  [ParagraphActions.SwitchEnable]: ['alt.ctrlCmd.r', 'alt.ctrlCmd.®'],
  // Need register special character `–` in MacOS
  [ParagraphActions.ReduceWidth]: ['alt.ctrlCmd.-', 'alt.ctrlCmd.–'],
  // Need register special character `≠` in MacOS
  [ParagraphActions.IncreaseWidth]: ['alt.ctrlCmd.+', 'alt.ctrlCmd.≠'],
  [ParagraphActions.Delete]: 'shift.delete',
  [ParagraphActions.MoveToUp]: ['ctrlCmd.k', 'ctrlCmd.arrowup', 'ctrlCmd.arrowleft'],
  [ParagraphActions.MoveToDown]: ['ctrlCmd.j', 'ctrlCmd.arrowdown', 'ctrlCmd.arrowright'],
  [ParagraphActions.SelectAbove]: ['k', 'arrowup', 'arrowleft'],
  [ParagraphActions.SelectBelow]: ['j', 'arrowdown', 'arrowright'],
  [ParagraphActions.SwitchLineNumber]: 'l',
  [ParagraphActions.SwitchTitleShow]: 't',
  [ParagraphActions.SwitchOutputShow]: 'o',
  [ParagraphActions.SwitchEditorShow]: 'e',
  [ParagraphActions.InsertAbove]: 'a',
  [ParagraphActions.InsertBelow]: 'b'
};

export interface ShortcutEvent {
  event: KeyboardEvent;
  keybindings: string;
}

export interface ShortcutOption {
  scope?: HTMLElement;
  keybindings: string;
}

function isMacOS() {
  return navigator.platform.indexOf('Mac') > -1;
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
    // `ctrlCmd` is special symbol, will be replaced `meta` in MacOS, 'control' in Windows/Linux
    const keybindings = option.keybindings.replace(/ctrlCmd/g, isMacOS() ? 'meta' : 'control');
    const eventName = `keydown.${keybindings}`;
    // tslint:disable-next-line:ban-types
    let dispose: Function;
    return new Observable<ShortcutEvent>(observer => {
      const handler = event => {
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
