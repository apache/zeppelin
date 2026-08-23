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
import { editor, IDisposable, IRange, languages, Position } from 'monaco-editor';

const MIN_PREFIX_LENGTH = 3;
const LOCAL_WINDOW_LINES = 400;
const MAX_CROSS_SCAN_LINES = 4000;

interface Registration {
  // Unused by the history tier; kept as the paragraph id the future LLM tier sends to the server.
  pid: string;
  sub: IDisposable;
}

/**
 * Ghost-text (inline) completions for the notebook editor.
 *
 * `suggest()` resolves a suggestion through tiers:
 * a future server-backed LLM tier falls back to the history tier below,
 * which repeats a line already written in the note.
 *
 * Opt-in via `?aiInlineComplete=true`; off by default.
 */
@Injectable({ providedIn: 'root' })
export class InlineCompletionService {
  private readonly languagesToRegister = ['python', 'scala'];
  // Plain Map (not WeakMap) so the history tier can scan every open paragraph.
  // Models self-remove on dispose, so they are never pinned in memory.
  private readonly models = new Map<editor.ITextModel, Registration>();
  private bound = false;

  register(model: editor.ITextModel, pid: string): void {
    if (!this.isEnabled() || this.models.has(model)) {
      return;
    }
    if (!this.bound) {
      this.bindProvider();
      this.bound = true;
    }
    const sub = model.onWillDispose(() => this.unregister(model));
    this.models.set(model, { pid, sub });
  }

  unregister(model: editor.ITextModel): void {
    const entry = this.models.get(model);
    if (entry) {
      entry.sub.dispose();
      this.models.delete(model);
    }
  }

  private isEnabled(): boolean {
    try {
      const searchParams = new URLSearchParams(window.location.search);
      const hashQuery = window.location.hash.split('?')[1] ?? '';
      const hashParams = new URLSearchParams(hashQuery);
      const isEnabled = (params: URLSearchParams) => {
        const value = params.get('aiInlineComplete');
        return value === 'true' || value === '';
      };

      return isEnabled(searchParams) || isEnabled(hashParams);
    } catch {
      return false;
    }
  }

  // Registered once for the app lifetime; the provider disposable is intentionally dropped.
  private bindProvider(): void {
    this.languagesToRegister.forEach(language => {
      languages.registerInlineCompletionsProvider(language, {
        provideInlineCompletions: (model: editor.ITextModel, position: Position): languages.InlineCompletions => {
          // The provider is global per language; only enabled, registered models get suggestions.
          if (!this.isEnabled() || !this.models.has(model)) {
            return { items: [] };
          }
          const insertText = this.suggest(model, position);
          if (!insertText) {
            return { items: [] };
          }
          const range: IRange = {
            startLineNumber: position.lineNumber,
            startColumn: position.column,
            endLineNumber: position.lineNumber,
            endColumn: position.column
          };
          return { items: [{ insertText, range }] };
        },
        freeInlineCompletions: (): void => {}
      });
    });
  }

  /** Seam for tiers. The LLM tier slots in above history in a follow-up; today only history runs. */
  private suggest(model: editor.ITextModel, position: Position): string | null {
    return this.historySuggestion(model, position);
  }

  /**
   * If a line elsewhere starts with the current line's text before the cursor,
   * offer the rest as ghost text:
   * the current cell first (nearest match), then other paragraphs.
   * Scans are line-budgeted so the per-keystroke cost stays bounded. Pure client-side.
   */
  private historySuggestion(model: editor.ITextModel, position: Position): string | null {
    // Only complete at end of line; otherwise the remainder would corrupt the existing suffix.
    const afterCursor = model.getLineContent(position.lineNumber).slice(position.column - 1);
    if (afterCursor.trim().length > 0) {
      return null;
    }

    const linePrefix = model.getValueInRange({
      startLineNumber: position.lineNumber,
      startColumn: 1,
      endLineNumber: position.lineNumber,
      endColumn: position.column
    });
    if (linePrefix.trim().length < MIN_PREFIX_LENGTH) {
      return null;
    }

    // Current cell: a window around the cursor, nearest match wins.
    const cursorIndex = position.lineNumber - 1;
    const local = this.matchInModel(
      model,
      linePrefix,
      cursorIndex,
      Math.max(0, cursorIndex - LOCAL_WINDOW_LINES),
      cursorIndex + LOCAL_WINDOW_LINES + 1
    );
    if (local !== null) {
      return local;
    }

    // Other paragraphs, same language, sharing one line budget so total cross-paragraph work per
    // keystroke stays bounded (a huge paragraph consumes the budget rather than blowing it up).
    let budget = MAX_CROSS_SCAN_LINES;
    for (const other of this.models.keys()) {
      if (other === model) {
        continue;
      }
      if (other.isDisposed()) {
        this.unregister(other);
        continue;
      }
      // Language checked here, not at register time, since a paragraph's language can change.
      if (other.getLanguageId() !== model.getLanguageId()) {
        continue;
      }
      if (budget <= 0) {
        break;
      }
      const remainder = this.matchInModel(other, linePrefix, -1, 0, budget);
      budget -= Math.min(other.getLineCount(), budget);
      if (remainder !== null) {
        return remainder;
      }
    }
    return null;
  }

  /**
   * Remainder of the line nearest `skipIndex` starting with `linePrefix`, within `[startLine, endLine)`.
   * Ties go to the earlier line; `skipIndex = -1` returns the topmost match.
   * Trailing whitespace is stripped and whitespace-only matches skipped.
   */
  private matchInModel(
    model: editor.ITextModel,
    linePrefix: string,
    skipIndex: number,
    startLine: number,
    endLine: number
  ): string | null {
    const limit = Math.min(endLine, model.getLineCount());
    let remainder: string | null = null;
    let bestDistance = Infinity;
    for (let i = Math.max(0, startLine); i < limit; i++) {
      if (i === skipIndex) {
        continue;
      }
      const line = model.getLineContent(i + 1);
      if (!line.startsWith(linePrefix)) {
        continue;
      }
      // trimEnd() is ES2019+ and this tsconfig's lib is es2018, so use a regex.
      const candidate = line.slice(linePrefix.length).replace(/\s+$/, '');
      if (candidate.length > 0) {
        if (skipIndex < 0) {
          return candidate;
        }
        const distance = Math.abs(i - skipIndex);
        if (distance < bestDistance) {
          bestDistance = distance;
          remainder = candidate;
        }
      }
    }
    return remainder;
  }
}
