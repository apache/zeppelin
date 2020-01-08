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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NotebookSearchResultItem } from '@zeppelin/interfaces';
import { NotebookSearchService } from '@zeppelin/services/notebook-search.service';
import { Subject } from 'rxjs';
import { filter, map, switchMap, takeUntil, tap } from 'rxjs/operators';

@Component({
  selector: 'zeppelin-notebook-search',
  templateUrl: './notebook-search.component.html',
  styleUrls: ['./notebook-search.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotebookSearchComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject();
  private searchAction$ = this.router.params.pipe(
    takeUntil(this.destroy$),
    map(params => params.queryStr),
    filter(queryStr => typeof queryStr === 'string' && !!queryStr.trim()),
    tap(() => (this.searching = true)),
    switchMap(queryStr => this.notebookSearchService.search(queryStr))
  );

  results: NotebookSearchResultItem[] = [];
  searching = false;

  constructor(
    private cdr: ChangeDetectorRef,
    private router: ActivatedRoute,
    private notebookSearchService: NotebookSearchService
  ) {}

  ngOnInit() {
    this.searchAction$.subscribe(results => {
      this.results = results;
      this.searching = false;
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    this.notebookSearchService.clear();
    this.destroy$.next();
    this.destroy$.complete();
  }
}
