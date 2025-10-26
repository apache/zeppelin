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

import { Injectable } from '@angular/core';
import { editor, languages, Position } from 'monaco-editor';
import { Subject } from 'rxjs';
import { filter, map, take } from 'rxjs/operators';

import { MessageListener, MessageListenersManager } from '@zeppelin/core';
import { CompletionReceived, OP } from '@zeppelin/sdk';

import { MessageService } from './message.service';

@Injectable({
  providedIn: 'root'
})
export class CompletionService extends MessageListenersManager {
  private completionLanguages = ['python', 'scala'];
  private completionItem$ = new Subject<CompletionReceived>();
  private receivers = new WeakMap<editor.ITextModel, string>();
  private bound = false;

  constructor(messageService: MessageService) {
    super(messageService);
  }

  @MessageListener(OP.COMPLETION_LIST)
  onCompletion(data: CompletionReceived): void {
    console.log('on receive!', data.id);
    this.completionItem$.next(data);
  }

  registerAsCompletionReceiver(model: editor.ITextModel, pid: string): void {
    if (this.receivers.has(model)) {
      return;
    }

    if (!this.bound) {
      this.bindMonacoCompletion();
      this.bound = true;
    }

    this.receivers.set(model, pid);
  }

  unregister(model: editor.ITextModel): void {
    this.receivers.delete(model);
  }

  private bindMonacoCompletion(): void {
    // eslint-disable-next-line @typescript-eslint/no-this-alias
    const that = this;

    this.completionLanguages.forEach(l => {
      languages.registerCompletionItemProvider(l, {
        provideCompletionItems(model: editor.ITextModel, position: Position) {
          const id = that.getIdForModel(model);
          const word = model.getWordUntilPosition(position);

          if (!id) {
            return { suggestions: [] };
          }

          that.messageService.completion(id, model.getValue(), model.getOffsetAt(position));

          return that.completionItem$
            .pipe(
              filter(d => d.id === id),
              take(1),
              map(d => ({
                suggestions: d.completions.map(
                  (i): languages.CompletionItem => ({
                    kind: languages.CompletionItemKind.Keyword,
                    label: i.name,
                    insertText: i.name,
                    range: {
                      startLineNumber: position.lineNumber,
                      endLineNumber: position.lineNumber,
                      startColumn: word.startColumn,
                      endColumn: word.endColumn
                    }
                  })
                )
              }))
            )
            .toPromise();
        }
      });
    });
  }

  private getIdForModel(model?: editor.ITextModel): string | null {
    if (!model) {
      return null;
    }
    return this.receivers.get(model) ?? null;
  }
}
