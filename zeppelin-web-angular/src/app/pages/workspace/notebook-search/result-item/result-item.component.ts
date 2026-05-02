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

import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NotebookSearchResultItem } from '@zeppelin/interfaces';

@Component({
  selector: 'zeppelin-notebook-search-result-item',
  templateUrl: './result-item.component.html',
  styleUrls: ['./result-item.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookSearchResultItemComponent implements OnChanges {
  @Input() result!: NotebookSearchResultItem;
  queryParams = {};
  displayName = '';
  routerLink: string[] = [];
  codeText = '';
  codeHtml = '';
  outputText = '';
  tablesText = '';
  interpreter = '';

  constructor(private router: ActivatedRoute) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.result) {
      this.parseResult();
    }
  }

  private parseResult(): void {
    const term = this.router.snapshot.params.queryStr;
    const listOfId = this.result.id.split('/');
    const [noteId, hasParagraph, paragraph] = listOfId;
    if (!hasParagraph) {
      this.routerLink = ['/', 'notebook', this.result.id];
      this.queryParams = {};
    } else {
      this.routerLink = ['/', 'notebook', noteId];
      this.queryParams = { paragraph, term };
    }
    this.displayName = this.result.name ? this.result.name : `Note ${noteId}`;

    // snippet = SQL/code, header = tables + output
    const snippet = this.result.snippet || '';
    // Preserve Lucene <B> highlighting by converting to <mark>
    this.codeHtml = snippet.replace(/<B>/gi, '<mark>').replace(/<\/B>/gi, '</mark>');
    this.codeText = snippet.replace(/<\/?B>/gi, '');
    this.interpreter = this.detectInterpreter(this.codeText);

    // Parse header: lines with [TABLES] prefix are tables, rest is output
    const header = (this.result.header || '').replace(/<\/?B>/gi, '');
    const lines = header.split('\n');
    const tableParts: string[] = [];
    const outputParts: string[] = [];
    for (const line of lines) {
      if (line.startsWith('[TABLES]')) {
        tableParts.push(line.substring(8).trim());
      } else if (line.trim()) {
        outputParts.push(line);
      }
    }
    this.tablesText = tableParts.join(', ');
    this.outputText = outputParts.join('\n');
  }

  private detectInterpreter(text: string): string {
    if (!text) {
      return '';
    }
    // Check interpreter prefix first — this is reliable
    if (/^%(\w*\.)?sql/i.test(text)) {
      return 'sql';
    }
    if (/^%(\w*\.)?py/i.test(text)) {
      return 'python';
    }
    if (/^%md/i.test(text)) {
      return 'md';
    }
    if (/^%sh/i.test(text)) {
      return 'sh';
    }
    // Fall back to keyword heuristic only if no prefix
    if (!text.startsWith('%')) {
      if (/\b(?:SELECT|INSERT|CREATE|FROM|WHERE)\b/i.test(text) && /\b(?:SELECT|FROM)\b/i.test(text)) {
        return 'sql';
      }
      if (/import |def |class /i.test(text)) {
        return 'python';
      }
    }
    return '';
  }
}
