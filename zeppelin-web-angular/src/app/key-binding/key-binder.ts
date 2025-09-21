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

import { ElementRef } from '@angular/core';
import { editor as MonacoEditor } from 'monaco-editor';
import { from, Subject } from 'rxjs';
import { map, mergeMap, takeUntil } from 'rxjs/operators';

import { ShortcutService } from '@zeppelin/services';
import * as _ from 'lodash';
import { KeyCodeConverter } from './key-code-converter';
import { ParagraphActions } from './paragraph-actions';
import { ShortcutsMap } from './shortcuts-map';

export class KeyBinder {
  private events$ = new Subject<{
    action: ParagraphActions;
    event: KeyboardEvent | null;
  }>();

  constructor(
    private destroySubject: Subject<unknown>,
    private host: ElementRef,
    private shortcutService: ShortcutService
  ) {}

  keyEvent() {
    return this.events$.asObservable();
  }

  initKeyBindingsOnAngular() {
    const shortcutService = this.shortcutService.forkByElement(this.host.nativeElement);
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
        takeUntil(this.destroySubject)
      )
      .subscribe(({ action, event }) => this.events$.next({ action, event }));
  }

  initKeyBindingsOnMonaco(editor: MonacoEditor.IStandaloneCodeEditor) {
    _.chain(ShortcutsMap)
      .toPairs()
      .flatMap(([action, keys]) => _.map(_.castArray(keys), key => ({ action, key })))

      .forEach(({ action, key }) => {
        const keyBinding = KeyCodeConverter.angularToMonacoKeyBinding(key);
        if (_.isNil(keyBinding)) {
          return;
        }
        editor.addCommand(keyBinding, () => {
          this.events$.next({ action: action as ParagraphActions, event: null });
        });
      })
      .value();
  }
}
