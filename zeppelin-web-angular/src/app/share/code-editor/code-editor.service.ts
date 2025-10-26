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
import { editor } from 'monaco-editor';

import {
  JoinedEditorOptions,
  NzCodeEditorConfig,
  NzCodeEditorLoadingStatus,
  NZ_CODE_EDITOR_CONFIG
} from './nz-code-editor.definitions';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function tryTriggerFunc(fn?: (...args: any[]) => any): (...args: any) => void {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (...args: any[]) => {
    if (fn) {
      fn(...args);
    }
  };
}

@Injectable({
  providedIn: 'root'
})
export class CodeEditorService {
  private document: Document;
  private firstEditorInitialized = false;
  private loaded$ = new Subject<boolean>();
  private loadingStatus = NzCodeEditorLoadingStatus.UNLOAD;
  private option: JoinedEditorOptions;

  option$: BehaviorSubject<JoinedEditorOptions>;

  constructor(
    @Inject(NZ_CODE_EDITOR_CONFIG) private config: NzCodeEditorConfig,
    @Inject(DOCUMENT) _document: any // eslint-disable-line  @typescript-eslint/no-explicit-any
  ) {
    this.document = _document;
    this.option = this.config.defaultEditorOption || {};
    this.option$ = new BehaviorSubject<JoinedEditorOptions>(this.option);
  }

  // TODO(hsuanxyz): use config service later.
  updateDefaultOption(option: JoinedEditorOptions): void {
    this.option = { ...this.option, ...option };
    this.option$.next(this.option);

    if ('theme' in option && option.theme) {
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
