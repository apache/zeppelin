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

import { Injectable, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class NgZService implements OnDestroy {
  private paragraphMap = new Map<string, {}>();
  private contextChange$ = new Subject<{
    paragraphId: string;
    key: string;
    // tslint:disable-next-line:no-any
    value: any;
    emit: boolean;
    set: boolean;
  }>();
  private runParagraph$ = new Subject<string>();

  constructor() {}

  contextChanged() {
    return this.contextChange$.asObservable();
  }

  runParagraphAction() {
    return this.runParagraph$.asObservable();
  }

  removeParagraph(paragraphId: string) {
    this.paragraphMap.delete(paragraphId);
  }

  runParagraph(paragraphId: string) {
    this.runParagraph$.next(paragraphId);
  }

  bindParagraph(paragraphId: string, context: {}) {
    this.paragraphMap.set(paragraphId, context);
  }

  setContextValue(key: string, value, paragraphId: string, emit = true) {
    const context = this.paragraphMap.get(paragraphId);
    if (context) {
      context[key] = value;
    }
    this.contextChange$.next({
      paragraphId,
      key,
      value,
      emit,
      set: true
    });
  }

  unsetContextValue(key: string, paragraphId: string, emit = true) {
    const context = this.paragraphMap.get(paragraphId);
    if (context) {
      context[key] = undefined;
    }
    this.contextChange$.next({
      paragraphId,
      key,
      emit,
      value: undefined,
      set: false
    });
  }

  ngOnDestroy(): void {
    this.paragraphMap.clear();
    this.contextChange$.complete();
  }
}
