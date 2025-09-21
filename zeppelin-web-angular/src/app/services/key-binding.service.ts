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

import { ElementRef, Injectable } from '@angular/core';
import { NotebookParagraphKeyboardEventHandler } from '@zeppelin/interfaces';
import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;
import { ParagraphActions, ShortcutsMap, ShortcutService } from '@zeppelin/services/shortcut.service';
import * as _ from 'lodash';
import { editor } from 'monaco-editor';
import { from, merge, Subject } from 'rxjs';
import { map, mergeMap, takeUntil } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class KeyBindingService {
  // If any ParagraphActions is missing here, TS compiler will complain.
  private actionMap: Record<ParagraphActions, keyof NotebookParagraphKeyboardEventHandler> = {
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
    [ParagraphActions.SwitchEnable]: 'handleSwitchEnable',
    [ParagraphActions.SwitchOutputShow]: 'handleSwitchOutputShow',
    [ParagraphActions.SwitchLineNumber]: 'handleSwitchLineNumber',
    [ParagraphActions.SwitchTitleShow]: 'handleSwitchTitleShow',
    [ParagraphActions.Clear]: 'handleClear',
    [ParagraphActions.Link]: 'handleLink',
    [ParagraphActions.ReduceWidth]: 'handleReduceWidth',
    [ParagraphActions.IncreaseWidth]: 'handleIncreaseWidth',
    [ParagraphActions.FindInCode]: 'handleFindInCode'
  };

  constructor(private shortcutService: ShortcutService) {}

  initKeyBindingsOnAngular(
    host: ElementRef,
    eventHandler: NotebookParagraphKeyboardEventHandler,
    destroySubject: Subject<unknown>
  ) {
    const shortcutService = this.shortcutService.forkByElement(host.nativeElement);
    from(Object.entries(ShortcutsMap))
      .pipe(
        mergeMap(([action, keys]) =>
          from(Array.isArray(keys) ? keys : [keys]).pipe(
            mergeMap(key =>
              shortcutService
                .bindShortcut({ keybindings: key })
                .pipe(map(({ event }) => ({ action: action as ParagraphActions, event })))
            )
          )
        ),
        takeUntil(destroySubject)
      )
      .subscribe(({ action, event }) => this.runParagraph(action, event, eventHandler));
  }

  private runParagraph(action: ParagraphActions, event: KeyboardEvent, handler: NotebookParagraphKeyboardEventHandler) {
    const target = event.target as HTMLElement;

    // Skip handling shortcut if focused element is an input (by Dynamic form)
    if (target.tagName === 'INPUT') {
      return; // ignore shortcut to make input work
    }

    const handlerFn = handler[this.actionMap[action]];
    if (!handlerFn) {
      throw new Error(`No handler for keyboard action '${action}'`);
    }
    // bind this to handlerFn so that it can access handler's properties'
    handlerFn.call(handler, event);
  }
}
