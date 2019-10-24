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
import { of as observableOf, BehaviorSubject, Observable, Subject } from 'rxjs';
import { map, tap } from 'rxjs/operators';

import {
  JoinedEditorOptions,
  NzCodeEditorConfig,
  NzCodeEditorLoadingStatus,
  NZ_CODE_EDITOR_CONFIG
} from './nz-code-editor.definitions';

import { editor } from 'monaco-editor';

// tslint:disable no-any
function tryTriggerFunc(fn?: (...args: any[]) => any): (...args: any) => void {
  return (...args: any[]) => {
    if (fn) {
      fn(...args);
    }
  };
}
// tslint:enable no-any

@Injectable({
  providedIn: 'root'
})
export class CodeEditorService {
  private document: Document;
  private firstEditorInitialized = false;
  private loaded$ = new Subject<boolean>();
  private loadingStatus = NzCodeEditorLoadingStatus.UNLOAD;
  private option: JoinedEditorOptions;

  option$ = new BehaviorSubject<JoinedEditorOptions>(this.option);

  constructor(
    @Inject(NZ_CODE_EDITOR_CONFIG) private config: NzCodeEditorConfig,
    @Inject(DOCUMENT) _document: any // tslint:disable-line no-any
  ) {
    this.document = _document;
    this.option = this.config.defaultEditorOption || {};
  }

  // TODO: use config service later.
  updateDefaultOption(option: JoinedEditorOptions): void {
    this.option = { ...this.option, ...option };
    this.option$.next(this.option);

    if (option.theme) {
      editor.setTheme(option.theme);
    }
  }

  requestToInit(): Observable<JoinedEditorOptions> {
    if (this.loadingStatus === NzCodeEditorLoadingStatus.LOADED) {
      this.onInit();
      return observableOf(this.getLatestOption());
    }

    if (this.loadingStatus === NzCodeEditorLoadingStatus.UNLOAD) {
      this.loadingStatus = NzCodeEditorLoadingStatus.LOADED;
      this.loaded$.next(true);
      this.loaded$.complete();
      this.onLoad();
      this.onInit();
      return observableOf(this.getLatestOption());
    }

    return this.loaded$.asObservable().pipe(
      tap(() => this.onInit()),
      map(() => this.getLatestOption())
    );
  }

  private onInit(): void {
    if (!this.firstEditorInitialized) {
      this.firstEditorInitialized = true;
      tryTriggerFunc(this.config.onFirstEditorInit)();
    }

    tryTriggerFunc(this.config.onInit)();
  }

  private onLoad(): void {
    tryTriggerFunc(this.config.onLoad)();
  }

  private getLatestOption(): JoinedEditorOptions {
    return { ...this.option };
  }
}
