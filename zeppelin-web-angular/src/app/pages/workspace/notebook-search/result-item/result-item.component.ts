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
import { ActivatedRoute, Router } from '@angular/router';
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
  titleHtml = '';
  interpreter = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.result) {
      this.parseResult();
    }
  }

  navigate(event: MouseEvent): void {
    const selection = window.getSelection();
    if (selection && selection.toString().length > 0) {
      return;
    }
    event.preventDefault();
    this.router.navigate(this.routerLink, { queryParams: this.queryParams });
  }

  private parseResult(): void {
    const term = this.route.snapshot.params.queryStr;
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

    const snippet = this.result.snippet || '';
    // Preserve Lucene <B> highlighting by converting to <mark>
    this.codeHtml = snippet.replace(/<B>/gi, '<mark>').replace(/<\/B>/gi, '</mark>');
    this.codeText = snippet.replace(/<\/?B>/gi, '');
    this.interpreter = this.detectInterpreter(this.codeText);

    const title = this.result.title || '';
    this.titleHtml = title.replace(/<B>/gi, '<mark>').replace(/<\/B>/gi, '</mark>');

    const tables = this.result.tables || '';
    this.tablesText = tables
      .trim()
      .split(/\s+/)
      .filter(t => t)
      .join(', ');
    this.outputText = this.result.output || '';
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
