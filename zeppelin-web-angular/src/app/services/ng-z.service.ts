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

import {
  AngularContext,
  AngularContextManager,
  AngularObjectBinding
} from '../core/paragraph-base/angular-context-manager';

interface ContextValue {
  value: unknown;
  binding?: AngularObjectBinding;
}

interface ParagraphBindings {
  noteId: string;
  outputContexts: Set<Record<string, unknown>>;
  localValues: Map<string, ContextValue>;
}

@Injectable({
  providedIn: 'root'
})
export class NgZService implements AngularContextManager, OnDestroy {
  private readonly paragraphBindingsById = new Map<string, ParagraphBindings>();
  private readonly boundValuesByNote = new Map<string, Map<string, Map<string, ContextValue>>>();
  private readonly contextChange$ = new Subject<AngularContext>();
  private readonly runParagraph$ = new Subject<string>();

  contextChanged() {
    return this.contextChange$.asObservable();
  }

  runParagraphAction() {
    return this.runParagraph$.asObservable();
  }

  registerParagraphBindings(noteId: string, paragraphId: string): void {
    const existing = this.paragraphBindingsById.get(paragraphId);
    if (existing?.noteId === noteId) {
      return;
    }
    this.removeParagraphBindings(paragraphId);
    this.paragraphBindingsById.set(paragraphId, { noteId, outputContexts: new Set(), localValues: new Map() });
  }

  removeParagraphBindings(paragraphId: string): void {
    const paragraph = this.paragraphBindingsById.get(paragraphId);
    if (!paragraph) {
      return;
    }
    this.paragraphBindingsById.delete(paragraphId);
    if (![...this.paragraphBindingsById.values()].some(target => target.noteId === paragraph.noteId)) {
      this.boundValuesByNote.delete(paragraph.noteId);
      return;
    }
    const noteBindings = this.boundValuesByNote.get(paragraph.noteId);
    for (const [key, values] of noteBindings ?? []) {
      for (const [identity, entry] of values) {
        if (entry.binding?.paragraphId === paragraphId) {
          values.delete(identity);
        }
      }
      if (values.size === 0) {
        noteBindings!.delete(key);
      }
    }
  }

  clearNoteContextValues(noteId: string): void {
    const keys = new Set(this.boundValuesByNote.get(noteId)?.keys());
    this.boundValuesByNote.delete(noteId);
    for (const [paragraphId, paragraph] of this.paragraphBindingsById) {
      if (paragraph.noteId !== noteId) {
        continue;
      }
      const paragraphKeys = new Set([...keys, ...paragraph.localValues.keys()]);
      paragraph.localValues.clear();
      for (const key of paragraphKeys) {
        this.updateParagraphContextValue(paragraphId, paragraph, key, false, false);
      }
    }
  }

  runParagraph(paragraphId: string) {
    this.runParagraph$.next(paragraphId);
  }

  bindParagraphContext(paragraphId: string, context: Record<string, unknown>): () => void {
    const paragraph = this.paragraphBindingsById.get(paragraphId);
    if (!paragraph) {
      return () => {};
    }
    paragraph.outputContexts.add(context);
    for (const key of new Set([
      ...paragraph.localValues.keys(),
      ...(this.boundValuesByNote.get(paragraph.noteId)?.keys() ?? [])
    ])) {
      context[key] = this.resolveContextValue(paragraphId, paragraph, key)?.value;
    }
    return () => paragraph.outputContexts.delete(context);
  }

  setContextValue(key: string, value: unknown, paragraphId: string, emit = true, binding?: AngularObjectBinding): void {
    const paragraph = this.paragraphBindingsById.get(paragraphId);
    if (!paragraph) {
      return;
    }
    if (binding) {
      let noteBindings = this.boundValuesByNote.get(paragraph.noteId);
      if (!noteBindings) {
        noteBindings = new Map();
        this.boundValuesByNote.set(paragraph.noteId, noteBindings);
      }
      let values = noteBindings.get(key);
      if (!values) {
        values = new Map();
        noteBindings.set(key, values);
      }
      const identity = JSON.stringify([binding.interpreterGroupId, binding.noteId, binding.paragraphId]);
      const previous = values.get(identity);
      if (!previous || previous.value !== value) {
        values.set(identity, { value, binding });
      }
      // Equal cached values must still reconcile fields changed directly by a template.
      if (binding.paragraphId !== null) {
        this.paragraphBindingsById.get(binding.paragraphId)?.localValues.delete(key);
      }
    } else {
      const current = this.resolveContextValue(paragraphId, paragraph, key);
      if (current?.binding) {
        current.value = value;
      } else {
        paragraph.localValues.set(key, { value });
      }
    }
    this.refreshNoteContextValue(paragraph.noteId, key, paragraphId, emit, true);
  }

  unsetContextValue(key: string, paragraphId: string, emit = true, binding?: AngularObjectBinding): void {
    const paragraph = this.paragraphBindingsById.get(paragraphId);
    if (!paragraph) {
      return;
    }
    const scope: AngularObjectBinding = binding ?? { noteId: paragraph.noteId, paragraphId };
    if (scope.noteId === paragraph.noteId && scope.paragraphId === paragraphId) {
      // Client bind excludes this connection from its echo; a local value may have no cached server entry yet.
      paragraph.localValues.delete(key);
    }
    const values = this.boundValuesByNote.get(paragraph.noteId)?.get(key);
    const matches = [...(values ?? [])].filter(
      ([, entry]) =>
        entry.binding?.noteId === scope.noteId &&
        entry.binding.paragraphId === scope.paragraphId &&
        (scope.interpreterGroupId === undefined || entry.binding.interpreterGroupId === scope.interpreterGroupId)
    );
    if (scope.interpreterGroupId !== undefined || matches.length <= 1) {
      for (const [identity] of matches) {
        values!.delete(identity);
      }
    }
    if (values?.size === 0) {
      this.boundValuesByNote.get(paragraph.noteId)!.delete(key);
    }
    this.refreshNoteContextValue(paragraph.noteId, key, paragraphId, emit, false);
  }

  ngOnDestroy(): void {
    this.paragraphBindingsById.clear();
    this.boundValuesByNote.clear();
    this.contextChange$.complete();
    this.runParagraph$.complete();
  }

  private resolveContextValue(
    paragraphId: string,
    paragraph: ParagraphBindings,
    key: string
  ): ContextValue | undefined {
    let resolved = paragraph.localValues.get(key);
    let priority = resolved ? 2 : -1;
    for (const entry of this.boundValuesByNote.get(paragraph.noteId)?.get(key)?.values() ?? []) {
      const scope = entry.binding!;
      if (scope.paragraphId !== null && scope.paragraphId !== paragraphId) {
        continue;
      }
      // Match the server's lookup order, including client-created paragraph values.
      const candidatePriority = scope.paragraphId !== null ? 2 : scope.noteId !== null ? 1 : 0;
      if (candidatePriority > priority) {
        resolved = entry;
        priority = candidatePriority;
      }
    }
    return resolved;
  }

  private refreshNoteContextValue(
    noteId: string,
    key: string,
    sourceParagraphId: string,
    emit: boolean,
    set: boolean
  ): void {
    for (const [paragraphId, paragraph] of this.paragraphBindingsById) {
      if (paragraph.noteId === noteId) {
        this.updateParagraphContextValue(paragraphId, paragraph, key, emit && paragraphId === sourceParagraphId, set);
      }
    }
  }

  private updateParagraphContextValue(
    paragraphId: string,
    paragraph: ParagraphBindings,
    key: string,
    emit: boolean,
    set: boolean
  ): void {
    const current = this.resolveContextValue(paragraphId, paragraph, key);
    for (const context of paragraph.outputContexts) {
      context[key] = current?.value;
    }
    this.contextChange$.next({
      noteId: paragraph.noteId,
      paragraphId,
      key,
      value: current?.value,
      binding: current?.binding,
      emit,
      set
    });
  }
}
