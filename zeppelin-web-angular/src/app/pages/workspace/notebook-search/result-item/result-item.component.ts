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

import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  SimpleChanges
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NotebookSearchResultItem } from '@zeppelin/interfaces';
import { JoinedEditorOptions } from '@zeppelin/share';
import { getKeywordPositions, KeywordPosition } from '@zeppelin/utility';
import { editor, Range } from 'monaco-editor';
import IEditor = editor.IEditor;
import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;

@Component({
  selector: 'zeppelin-notebook-search-result-item',
  templateUrl: './result-item.component.html',
  styleUrls: ['./result-item.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookSearchResultItemComponent implements OnChanges, OnDestroy {
  @Input() result!: NotebookSearchResultItem;
  queryParams = {};
  displayName = '';
  routerLink: string[] = [];
  mergedStr?: string;
  keywords: string[] = [];
  highlightPositions: KeywordPosition[] = [];
  editor?: IStandaloneCodeEditor;
  height = 0;
  decorations: string[] = [];
  editorOption = {
    readOnly: true,
    fontSize: 12,
    renderLineHighlight: 'none',
    minimap: { enabled: false },
    lineNumbers: 'off',
    glyphMargin: false,
    scrollBeyondLastLine: false,
    contextmenu: false,
    scrollbar: {
      handleMouseWheel: false,
      alwaysConsumeMouseWheel: false
    }
  } as JoinedEditorOptions;

  constructor(private ngZone: NgZone, private cdr: ChangeDetectorRef, private router: ActivatedRoute) {}

  setDisplayNameAndRouterLink(): void {
    const term = this.router.snapshot.params.queryStr;
    const listOfId = this.result.id.split('/');
    const [noteId, hasParagraph, paragraph] = listOfId;
    if (!hasParagraph) {
      this.routerLink = ['/', 'notebook', this.result.id];
      this.queryParams = {};
    } else {
      this.routerLink = ['/', 'notebook', noteId];
      this.queryParams = {
        paragraph,
        term
      };
    }
    this.displayName = this.result.name ? this.result.name : `Note ${noteId}`;
  }

  setHighlightKeyword(): void {
    let mergedStr = this.result.header ? `${this.result.header}\n\n${this.result.snippet}` : this.result.snippet;

    const regexp = /<B>(.+?)<\/B>/g;
    const matches = [];
    let match = regexp.exec(mergedStr);

    while (match !== null) {
      if (match[1]) {
        matches.push(match[1].toLocaleLowerCase());
      }
      match = regexp.exec(mergedStr);
    }

    mergedStr = mergedStr.replace(regexp, '$1');
    this.mergedStr = mergedStr;
    const keywords = [...new Set(matches)];
    this.highlightPositions = getKeywordPositions(keywords, mergedStr);
  }

  applyHighlight() {
    if (this.editor) {
      this.decorations = this.editor.deltaDecorations(
        this.decorations,
        this.highlightPositions.map(highlight => {
          const line = highlight.line + 1;
          const character = highlight.character + 1;
          return {
            range: new Range(line, character, line, character + highlight.length),
            options: {
              className: 'mark',
              stickiness: 1
            }
          };
        })
      );
      this.cdr.markForCheck();
    }
  }

  setLanguage() {
    const model = this.editor?.getModel();
    if (!model) {
      throw new Error('Editor model is not defined.');
    }
    const editorModes = {
      scala: /^%(\w*\.)?(spark|flink)/,
      python: /^%(\w*\.)?(pyspark|python)/,
      html: /^%(\w*\.)?(angular|ng)/,
      r: /^%(\w*\.)?(r|sparkr|knitr)/,
      sql: /^%(\w*\.)?\wql/,
      yaml: /^%(\w*\.)?\wconf/,
      markdown: /^%md/,
      shell: /^%sh/
    };
    let mode = 'text';
    for (const [modeOption, regex] of Object.entries(editorModes)) {
      if (regex.test(this.result.snippet)) {
        mode = modeOption;
        break;
      }
    }
    editor.setModelLanguage(model, mode);
  }

  autoAdjustEditorHeight() {
    this.ngZone.run(() => {
      setTimeout(() => {
        const model = this.editor?.getModel();
        if (model) {
          this.height = this.editor!.getOption(monaco.editor.EditorOption.lineHeight) * (model.getLineCount() + 2);
          this.editor!.layout();
          this.cdr.markForCheck();
        }
      });
    });
  }

  initializedEditor(editorInstance: IEditor) {
    this.editor = editorInstance as IStandaloneCodeEditor;
    this.editor.setValue(this.mergedStr ?? '');
    this.setLanguage();
    this.autoAdjustEditorHeight();
    this.applyHighlight();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.result) {
      this.setDisplayNameAndRouterLink();
      this.setHighlightKeyword();
      this.autoAdjustEditorHeight();
      this.applyHighlight();
    }
  }

  ngOnDestroy(): void {
    this.editor?.dispose();
  }
}
