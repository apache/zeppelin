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

import { Component, DoCheck, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { DatasetType, Note } from '@zeppelin/sdk';

interface TocResult {
  paragraphId: string;
  resultData: string;
  resultType: DatasetType;
}

interface TocRow {
  paragraphId: string;
  level: number;
  title: string;
}

@Component({
  selector: 'zeppelin-note-toc',
  templateUrl: './note-toc.component.html',
  styleUrls: ['./note-toc.component.less']
})
export class NoteTocComponent implements OnInit, DoCheck {
  @Input() note: Note['note'];
  @Output() readonly scrollToParagraph = new EventEmitter<string>();
  Arr = Array;
  rows = [];
  oldNote: Note['note'];

  onRowClick(id: string) {
    this.scrollToParagraph.emit(id);
  }

  getResults(note: Note['note']): TocResult[] {
    const results = note.paragraphs.reduce((allResults: TocResult[], paragraph) => {
      const newResults = [];
      if (paragraph.results && paragraph.results.msg) {
        paragraph.results.msg.forEach(result =>
          newResults.push({
            paragraphId: paragraph.id,
            resultData: result.data,
            resultType: result.type
          })
        );
      }
      return [...allResults, ...newResults];
    }, []);
    return results.filter(result => result.resultType === DatasetType.HTML);
  }

  unpackNodes(element: Element) {
    element.querySelectorAll('*').forEach(subElements => (subElements.outerHTML = subElements.innerHTML));
    return element.innerHTML;
  }

  computeRows() {
    const htmlResults = this.getResults(this.note);
    const rows: TocRow[] = htmlResults.reduce((allRows: TocRow[], result) => {
      const parser = new DOMParser();
      const resultDOM = parser.parseFromString(result.resultData, 'text/html');
      const headings = Array.from(resultDOM.querySelectorAll('h1, h2, h3, h4, h5, h6'));
      const newRows = headings.map(heading => ({
        level: parseInt(heading.nodeName[1], 10),
        title: this.unpackNodes(heading),
        paragraphId: result.paragraphId
      }));
      return [...allRows, ...newRows];
    }, []);
    const levelsSet: Set<number> = new Set();
    rows.forEach(row => levelsSet.add(row.level));
    const levels = Array.from(levelsSet).sort();
    const headingLevelToTocLevelMap = {};
    levels.forEach((level, index) => (headingLevelToTocLevelMap[level] = index + 1));
    this.rows = rows.map(heading => ({ ...heading, level: headingLevelToTocLevelMap[heading.level] }));
  }

  shouldRecomputeRows() {
    if (this.note.id !== this.oldNote.id) {
      return true;
    }
    const oldResult = this.getResults(this.oldNote);
    return this.getResults(this.note).reduce(
      (hasParagraphUpdated, result, resultIndex) =>
        hasParagraphUpdated || !oldResult[resultIndex] || result.resultData !== oldResult[resultIndex].resultData,
      false
    );
  }

  ngOnInit() {
    this.computeRows();
    this.oldNote = this.note;
  }

  ngDoCheck() {
    if (this.shouldRecomputeRows()) {
      this.computeRows();
      this.oldNote = this.note;
    }
  }
}
