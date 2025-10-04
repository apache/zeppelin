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
